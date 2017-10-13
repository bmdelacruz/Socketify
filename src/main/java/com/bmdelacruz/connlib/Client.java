package com.bmdelacruz.connlib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Client {
    private final InetSocketAddress socketAddress;
    private final ArrayList<Listener> listeners;
    private final int bufferSize;

    private SocketChannel socketChannel;
    private Selector selector;

    private PendingData pendingData;

    private Thread clientThread;

    public interface Listener {
        void onDataReceived(byte[] data);
        void onServerDisconnect();
    }

    public Client(int portToConnectTo) {
        this(portToConnectTo, "localhost");
    }

    public Client(int portToConnectTo, String address) {
        this(portToConnectTo, address, 1024);
    }

    public Client(int portToConnectTo, String address, int bufferSize) {
        this.bufferSize = bufferSize;

        socketAddress = new InetSocketAddress(address, portToConnectTo);
        listeners = new ArrayList<>();
    }

    public SelectionKeyProcessor createSelectionKeyProcessor(int bufferSize) {
        return new ClientSelectionKeyProcessor(bufferSize);
    }

    public final void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public final void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public final void connect() throws IOException {
        selector = Selector.open();
        pendingData = new PendingData();

        socketChannel = SocketChannel.open(this.socketAddress);
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);

        clientThread = new Thread(new ClientRunnable());
        clientThread.start();
    }

    public void disconnect() throws IOException {
        clientThread.interrupt();
    }

    public void sendBytes(byte[] data) throws IOException {
        ByteBuffer dataBuffer = (ByteBuffer) ByteBuffer.allocate(data.length + 1)
                .put(data).put((byte) 0x00).flip();
        socketChannel.write(dataBuffer);
        dataBuffer.clear();
    }

    private void read(SelectionKey key) throws IOException {
        createSelectionKeyProcessor(bufferSize).read(key);
    }

    private class ClientRunnable implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    selector.select();

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) continue;

                        if (key.isReadable()) {
                            read(key);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                selector.close();
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ClientSelectionKeyProcessor extends SelectionKeyProcessor {
        public ClientSelectionKeyProcessor(int bufferSize) {
            super(bufferSize);
        }

        @Override
        public List<byte[]> getPendingReadList(SelectionKey key) {
            return pendingData.getPendingData();
        }

        @Override
        public void processCompleteData(SelectionKey key, byte[] data) {
            for (Listener listener : listeners) {
                listener.onDataReceived(data);
            }
        }

        @Override
        public void onDisconnect(SelectionKey key) {
            try {
                key.cancel();
                key.channel().close();

                for (Listener listener : listeners) {
                    listener.onServerDisconnect();
                }
            } catch (IOException ignored) {}
        }

        @Override
        public void onConnectionFailure(SelectionKey key) {
            try {
                key.cancel();
                key.channel().close();

                for (Listener listener : listeners) {
                    listener.onServerDisconnect();
                }
            } catch (IOException ignored) {}
        }
    }
}
