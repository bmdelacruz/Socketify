package com.bmdelacruz.connlib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Server {
    private final InetSocketAddress serverAddress;
    private final List<Listener> listeners;
    private final int bufferSize;

    private Thread serverThread;
    private Selector selector;

    private HashMap<SocketChannel, ClientConnection> clientConnections;
    private HashMap<SocketChannel, PendingData> pendingWrites;
    private HashMap<SocketChannel, PendingData> pendingReads;

    public interface Listener {
        void onClientConnect(ClientConnection clientConnection);
        void onClientMessageFailed(ClientConnection clientConnection, Exception e);
    }

    public Server(int port) {
        this(port, 1024);
    }

    public Server(int port, int bufferSize) {
        this.bufferSize = bufferSize;

        serverAddress = new InetSocketAddress(port);
        listeners = new ArrayList<>();
    }

    public ClientConnection createClientConnection(SocketChannel socketChannel) {
        return new ClientConnection(socketChannel);
    }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public void start() throws IOException {
        clientConnections = new HashMap<>();
        pendingWrites = new HashMap<>();
        pendingReads = new HashMap<>();
        selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(serverAddress);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        serverThread = new Thread(new ServerRunnable());
        serverThread.start();
    }

    public void stop() {
        this.serverThread.interrupt();
    }

    public boolean sendTo(SocketChannel socketChannel, byte[] data) throws InterruptedException {
        SelectionKey key = getKeyFor(socketChannel);
        if (key != null) {
            sendTo(key, data);
            return true;
        } else {
            return false;
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();

        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);

            ClientConnection clientConnection = createClientConnection(socketChannel);
            clientConnections.put(socketChannel, clientConnection);

            for (Listener listener : listeners)
                listener.onClientConnect(clientConnection);
        }
    }

    private void read(SelectionKey key) {
        SelectionKeyProcessor skp = new SelectionKeyProcessor(bufferSize) {
            @Override
            public List<byte[]> getPendingReadList(SelectionKey key) {
                SocketChannel socketChannel = (SocketChannel) key.channel();

                if (!pendingReads.containsKey(socketChannel))
                    pendingReads.put(socketChannel, new PendingData());

                PendingData pendingData = pendingReads.get(socketChannel);
                return pendingData.getPendingData();
            }

            @Override
            public void processCompleteData(SelectionKey key, byte[] data) {
                SocketChannel socketChannel = (SocketChannel) key.channel();
                ClientConnection clientConnection = clientConnections.get(socketChannel);

                clientConnection.onDataReceived(data, new ServerMessenger(key));
            }

            @Override
            public void onDisconnect(SelectionKey key) {
                ClientConnection clientConnection = endAndReturnConnection(key);
                if (clientConnection != null) {
                    clientConnection.onDisconnected();
                }
            }

            @Override
            public void onConnectionFailure(SelectionKey key) {
                ClientConnection clientConnection = endAndReturnConnection(key);
                if (clientConnection != null) {
                    clientConnection.onFailure();
                }
            }

            private ClientConnection endAndReturnConnection(SelectionKey key) {
                try {
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    clientConnections.remove(socketChannel);

                    key.cancel();
                    key.channel().close();

                    return clientConnections.get(socketChannel);
                } catch (IOException ignored) {
                    return null;
                }
            }
        };
        skp.read(key);
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ClientConnection clientConnection = clientConnections.get(socketChannel);

        PendingData pendingData = pendingWrites.get(socketChannel);
        synchronized (pendingWrites.get(socketChannel)) {
            pendingData.setHasPendingData(true);

            List<byte[]> pending = pendingData.getPendingData();
            while (pending.size() > 0) {
                try {
                    byte[] data = pending.remove(0);
                    ByteBuffer dataBuffer = (ByteBuffer) ByteBuffer.allocate(data.length + 1)
                            .put(data).put((byte) 0x00).flip();
                    socketChannel.write(dataBuffer);
                } catch (IOException e) {
                    key.cancel();
                    socketChannel.close();
                    clientConnections.remove(socketChannel);

                    clientConnection.onFailure();

                    for (Listener listener : listeners)
                        listener.onClientMessageFailed(clientConnection, e);
                }
            }

            pendingData.setHasPendingData(false);
            pendingData.notifyAll();
        }

        if (key.isValid())
            key.interestOps(SelectionKey.OP_READ);
    }

    private void sendTo(SelectionKey key, byte[] data) throws InterruptedException {
        key.interestOps(SelectionKey.OP_WRITE);

        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (!pendingWrites.containsKey(socketChannel))
            pendingWrites.put(socketChannel, new PendingData());

        PendingData pendingData = pendingWrites.get(socketChannel);
        synchronized (pendingWrites.get(socketChannel)) {
            while (pendingData.hasPendingData())
                pendingData.wait();

            pendingData.getPendingData().add(data);
        }
    }

    private SelectionKey getKeyFor(SocketChannel socketChannel) {
        for (SelectionKey key : selector.keys())
            if (key.channel() == socketChannel)
                return key;
        return null;
    }

    private class ServerRunnable implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    selector.select();

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (key.isValid()) {
                            if (key.isAcceptable()) {
                                accept(key);
                            } else if (key.isReadable()) {
                                read(key);
                            } else if (key.isWritable()) {
                                write(key);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ServerMessenger implements ClientConnection.Messenger {
        private final SelectionKey key;

        public ServerMessenger(SelectionKey key) {
            this.key = key;
        }

        @Override
        public void reply(byte[] data) {
            if (data != null) {
                try {
                    sendTo(key, data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}