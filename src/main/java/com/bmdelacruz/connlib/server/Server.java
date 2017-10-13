package com.bmdelacruz.connlib.server;

import com.bmdelacruz.connlib.commons.PendingData;
import com.bmdelacruz.connlib.commons.SelectionKeyProcessor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Server {
    public static final int DEFAULT_BUFFER_SIZE = 1024;

    private final InetSocketAddress serverAddress;
    private final int bufferSize;

    private Listener listener;

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
        this(port, DEFAULT_BUFFER_SIZE);
    }

    public Server(int port, int bufferSize) {
        this.bufferSize = bufferSize;

        serverAddress = new InetSocketAddress(port);
    }

    /**
     * Builds a ClientConnection instance.
     * @param socketChannel The SocketChannel to be associated with the ClientConnection instance.
     * @return The newly created ClientConnection instance.
     */
    public ClientConnection createClientConnection(SocketChannel socketChannel) {
        return new ClientConnection(socketChannel);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Start listening for client connections.
     * @throws IOException Thrown when something went wrong while setting up the server.
     */
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

    /**
     * Stop serving clients.
     */
    public void stop() {
        this.serverThread.interrupt();
    }

    /**
     * Sends the data to the specified ClientConnection.
     * @param clientConnection The client which will receive the data.
     * @param data The data to be transferred to the client.
     * @return <code>true</code> if the data was sent to the client.
     * @throws InterruptedException Thrown when the server was stopped while trying to send the data.
     */
    public boolean sendTo(ClientConnection clientConnection, byte[] data) throws InterruptedException {
        SelectionKey key = getKeyFor(clientConnection.getSocketChannel());
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

            if (listener != null)
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
                    key.cancel();
                    key.channel().close();

                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    return clientConnections.remove(socketChannel);
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

                    if (listener != null)
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