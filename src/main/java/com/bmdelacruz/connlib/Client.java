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

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public void connect() throws IOException {
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
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        try {
            int numOfReadBytes = socketChannel.read(buffer);
            buffer.flip();

            if (numOfReadBytes == -1) {
                key.cancel();
                socketChannel.close();
                for (Listener listener : listeners)
                    listener.onServerDisconnect();
            } else {
                List<byte[]> pendingDataList = pendingData.getPendingData();

                // for complete unread data but included in buffer together
                // with the pending data which completes the previous message
                List<byte[]> completeUnreadDataList = new ArrayList<>();
                ByteBuffer remainingDataBuffer = null;

                boolean hasCompleteMessage = false;
                boolean isNullByteFound = false;

                // iterate over the buffer to find '0x00'
                while (buffer.hasRemaining() && !isNullByteFound) {
                    // check if the byte at the next position is '0x00'
                    isNullByteFound = hasCompleteMessage = buffer.get() == (byte) 0x00;

                    if (isNullByteFound) {
                        remainingDataBuffer = buffer.slice();

                        buffer.reset();
                        buffer.flip();

                        byte[] newData = Utils.extractBytesFrom(buffer);
                        if (pendingData.hasPendingData()) {
                            // CASE 2: Overflowing
                            // there are pending data and the end of the data is finally
                            // encountered in the buffer

                            pendingDataList.add(newData);

                            byte[] completeData = Utils.concatenate(pendingDataList);
                            completeUnreadDataList.add(completeData);

                            // clear pending data list and flag for this socket channel
                            pendingDataList.clear();
                            pendingData.setHasPendingData(false);
                        } else {
                            // CASE 1: Complete
                            // the start and the end of the data is in the buffer

                            completeUnreadDataList.add(newData);
                        }
                    } else {
                        buffer.mark();
                    }
                }

                if (hasCompleteMessage) {
                    // CASE 3: Overflowing w/ more data
                    // there is already a complete message but the buffer may still
                    // contain more complete or incomplete data

                    boolean isRemNullByteFound = false;
                    while (remainingDataBuffer != null && remainingDataBuffer.hasRemaining()) {
                        while (remainingDataBuffer.hasRemaining()) {
                            isRemNullByteFound = remainingDataBuffer.get() == (byte) 0x00;

                            if (isRemNullByteFound) {
                                ByteBuffer temp = remainingDataBuffer.slice();

                                remainingDataBuffer.reset();
                                remainingDataBuffer.flip();

                                byte[] newData = Utils.extractBytesFrom(remainingDataBuffer);
                                completeUnreadDataList.add(newData);

                                remainingDataBuffer = temp;
                            } else {
                                remainingDataBuffer.mark();
                            }
                        }

                        if (!isRemNullByteFound) {
                            remainingDataBuffer.flip();

                            // when the end of the message was not found in the remaining data in buffer
                            byte[] newData = Utils.extractBytesFrom(remainingDataBuffer);
                            pendingDataList.add(newData);

                            pendingData.setHasPendingData(true);
                        }
                    }

                    for (byte[] data : completeUnreadDataList)
                        for (Listener listener : listeners)
                            listener.onDataReceived(data);

                    completeUnreadDataList.clear();
                } else {
                    buffer.flip();

                    // when the end of the message was not found in the data in the buffer
                    byte[] newData = Utils.extractBytesFrom(buffer);
                    pendingDataList.add(newData);

                    pendingData.setHasPendingData(true);
                }
            }
        } catch (IOException e) {
            key.cancel();
            socketChannel.close();

            for (Listener listener : listeners)
                listener.onServerDisconnect();
        }
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
}
