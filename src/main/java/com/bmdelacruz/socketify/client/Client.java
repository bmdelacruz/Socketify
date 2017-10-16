package com.bmdelacruz.socketify.client;

import com.bmdelacruz.socketify.commons.PendingData;
import com.bmdelacruz.socketify.commons.SelectionKeyProcessor;
import com.bmdelacruz.socketify.data.DataProcessor;
import com.bmdelacruz.socketify.data.DataProcessorChain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

public class Client {
    private final InetSocketAddress socketAddress;
    private final int bufferSize;

    private Listener listener;

    private SocketChannel socketChannel;
    private Selector selector;
    private PendingData pendingData;

    private Thread clientThread;

    private DataProcessorChain readDataProcessorChain;
    private DataProcessorChain writeDataProcessorChain;

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
        readDataProcessorChain = new DataProcessorChain();
        writeDataProcessorChain = new DataProcessorChain();
    }

    public void addReadDataProcessor(DataProcessor dataProcessor) {
        readDataProcessorChain.addDataProcessor(dataProcessor);
    }

    public void addWriteDataProcessor(DataProcessor dataProcessor) {
        writeDataProcessorChain.addDataProcessor(dataProcessor);
    }

    public final boolean isConnected() {
        return clientThread.isAlive();
    }

    public final void setListener(Listener listener) {
        this.listener = listener;
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

    public final void disconnect() throws IOException {
        if (!isConnected()) return;

        clientThread.interrupt();
    }

    public final void sendBytes(byte[] data) throws IOException {
        if (!isConnected()) return;

        data = writeDataProcessorChain.process(data);

        ByteBuffer dataBuffer = (ByteBuffer) ByteBuffer.allocate(data.length + 1)
                .put(data).put((byte) 0x00).flip();
        socketChannel.write(dataBuffer);
        dataBuffer.clear();
    }

    private void read(SelectionKey key) throws IOException {
        SelectionKeyProcessor skp = new SelectionKeyProcessor() {
            @Override
            public List<byte[]> getPendingReadList(SelectionKey key) {
                return pendingData.getPendingData();
            }

            @Override
            public void processCompleteData(SelectionKey key, byte[] data) {
                if (listener != null) {
                    data = readDataProcessorChain.process(data);
                    listener.onDataReceived(data);
                }
            }

            @Override
            public void onDisconnect(SelectionKey key) {
                try {
                    key.cancel();
                    key.channel().close();

                    if (listener != null)
                        listener.onServerDisconnect();
                } catch (IOException ignored) {}
            }

            @Override
            public void onConnectionFailure(SelectionKey key) {
                try {
                    key.cancel();
                    key.channel().close();

                    if (listener != null)
                        listener.onServerDisconnect();
                } catch (IOException ignored) {}
            }
        };
        skp.read(key);
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

                        if (!key.isValid())
                            continue;
                        if (key.isReadable())
                            read(key);
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
}
