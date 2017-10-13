package com.bmdelacruz.connlib;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class FindableServer extends Server {
    private final InetSocketAddress findableAddress;
    private final int datagramBufferSize;

    private Thread findableServerThread;
    private Selector selector;

    private HashMap<DatagramChannel, PendingData> pendingWrites;
    private List<Listener> listeners;

    public interface Listener {
        void onFound();
    }

    public FindableServer(int port, int discoverablePort, int bufferSize, int datagramBufferSize) {
        super(port, bufferSize);
        this.datagramBufferSize = datagramBufferSize;
        this.listeners = new ArrayList<>();

        findableAddress = new InetSocketAddress(discoverablePort);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public byte[] createReplyData(byte[] receivedData) {
        return findableAddress.getAddress().getAddress();
    }

    public boolean getReplyCondition(byte[] receivedData) {
        return new String(receivedData).equals("FIND");
    }

    public void startDiscoverability() throws IOException {
        pendingWrites = new HashMap<>();

        DatagramChannel datagramChannel = DatagramChannel.open(StandardProtocolFamily.INET)
                    .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .bind(this.findableAddress);
        datagramChannel.configureBlocking(false);

        selector = Selector.open();
        datagramChannel.register(this.selector, SelectionKey.OP_READ);

        findableServerThread = new Thread(new FindableServerRunnable());
        findableServerThread.start();
    }

    public void stopDiscoverability() {
        this.findableServerThread.interrupt();
    }

    private void writeDatagram(SelectionKey key) throws IOException {
        DatagramChannel datagramChannel = ((DatagramChannel) key.channel());

        DatagramPendingData pendingWrite = (DatagramPendingData) pendingWrites.get(datagramChannel);
        List<byte[]> pendingData = pendingWrite.getPendingData();

        for (byte[] data : pendingData)
            datagramChannel.send(ByteBuffer.wrap(data), pendingWrite.getSocketAddress());

        pendingData.clear();

        key.interestOps(SelectionKey.OP_READ);
    }

    private void readDatagram(SelectionKey key) throws IOException {
        DatagramChannel datagramChannel = ((DatagramChannel) key.channel());
        ByteBuffer buffer = ByteBuffer.allocate(datagramBufferSize);

        SocketAddress socketAddress = datagramChannel.receive(buffer);
        buffer.flip();

        byte[] receivedData = new byte[buffer.limit()];
        buffer.get(receivedData);

        if (getReplyCondition(receivedData)) {
            byte[] replyData = createReplyData(receivedData);
            if (!pendingWrites.containsKey(datagramChannel))
                pendingWrites.put(datagramChannel, new DatagramPendingData());

            DatagramPendingData pendingData = (DatagramPendingData) pendingWrites.get(datagramChannel);
            pendingData.getPendingData().add(replyData);
            pendingData.setSocketAddress(socketAddress);

            key.interestOps(SelectionKey.OP_WRITE);

            for (Listener listener : listeners)
                listener.onFound();
        }
    }

    private class DatagramPendingData extends PendingData {
        private SocketAddress socketAddress;

        public SocketAddress getSocketAddress() {
            return socketAddress;
        }

        public void setSocketAddress(SocketAddress socketAddress) {
            this.socketAddress = socketAddress;
        }
    }

    private class FindableServerRunnable implements Runnable {
        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    selector.select();

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while(keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) continue;

                        if (key.isReadable()) {
                            readDatagram(key);
                        } else if (key.isWritable()) {
                            writeDatagram(key);
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
}
