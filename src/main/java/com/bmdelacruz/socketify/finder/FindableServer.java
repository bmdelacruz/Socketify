package com.bmdelacruz.socketify.finder;

import com.bmdelacruz.socketify.commons.PendingData;
import com.bmdelacruz.socketify.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class FindableServer extends Server {
    public static final int DEFAULT_DATAGRAM_BUFFER_SIZE = 1024;

    private final InetSocketAddress findableAddress;
    private final int datagramBufferSize;

    private Thread findableServerThread;
    private Selector selector;
    private DatagramChannel datagramChannel;

    private HashMap<DatagramChannel, PendingData> pendingWrites;
    private Listener listener;

    public interface Listener {
        void onFound();
    }

    public FindableServer(int port, int discoverablePort) {
        this(port, discoverablePort, DEFAULT_BUFFER_SIZE, DEFAULT_DATAGRAM_BUFFER_SIZE);
    }

    public FindableServer(int port, int discoverablePort, int bufferSize, int datagramBufferSize) {
        super(port, bufferSize);
        this.datagramBufferSize = datagramBufferSize;

        findableAddress = new InetSocketAddress(discoverablePort);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * This normally returns the address of the server in bytes (network byte order).
     * Override this to create a send ServerFinder instances a different data.
     * @param receivedData The data received by the server.
     * @return The data to be sent to the ServerFinder instances.
     */
    public byte[] createReplyData(byte[] receivedData) {
        return findableAddress.getAddress().getAddress();
    }

    /**
     * The condition which will be checked when the server should reply to the packet
     * sent by the ServerFinder instances. Override this to change the default condition
     * which is <code>new String(receivedData).equals("FIND")</code>.
     * @param receivedData The data received by the server.
     * @return <code>true</code> if the condition is met.
     */
    public boolean getReplyCondition(byte[] receivedData) {
        return new String(receivedData).equals("FIND");
    }

    /**
     * Start the discoverability of this server.
     * @throws IOException Thrown when something went wrong while setting up the
     * server's discoverability.
     */
    public void startDiscoverability() throws IOException {
        pendingWrites = new HashMap<>();

        datagramChannel = DatagramChannel.open()
                    .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .bind(this.findableAddress);
        datagramChannel.configureBlocking(false);

        selector = Selector.open();
        datagramChannel.register(this.selector, SelectionKey.OP_READ);

        findableServerThread = new Thread(new FindableServerRunnable());
        findableServerThread.start();
    }

    /**
     * Stop the discoverability of this server.
     */
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

            if (listener != null)
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
                    selector.select(100);

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
                datagramChannel.close();
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
