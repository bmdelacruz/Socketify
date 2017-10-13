package com.bmdelacruz.connlib;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class DiscoverableServer extends Server {
    private final InetSocketAddress discoverableAddress;
    private final int datagramBufferSize;

    private Thread discoverableServerThread;
    private Selector selector;

    private HashMap<DatagramChannel, PendingData> pendingWrites;

    public DiscoverableServer(int port, int discoverablePort, int bufferSize, int datagramBufferSize) {
        super(port, bufferSize);
        this.datagramBufferSize = datagramBufferSize;

        InetAddress localAddress = getLocalAddress();
        if (localAddress == null)
            throw new IllegalStateException("No network interface available.");

        discoverableAddress = new InetSocketAddress(localAddress, discoverablePort);
    }

    public byte[] createReplyData(byte[] receivedData) {
        return discoverableAddress.getAddress().getAddress();
    }

    public void startDiscoverability() throws IOException {
        pendingWrites = new HashMap<>();

        DatagramChannel datagramChannel =
                DatagramChannel.open(StandardProtocolFamily.INET)
                    .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .bind(this.discoverableAddress);
        datagramChannel.configureBlocking(false);

        selector = Selector.open();
        datagramChannel.register(this.selector, SelectionKey.OP_READ);

        discoverableServerThread = new Thread(new DiscoverableServerRunnable());
        discoverableServerThread.start();
    }

    public void stopDiscoverability() {
        this.discoverableServerThread.interrupt();
    }

    private void write(SelectionKey key) throws IOException {
        DatagramChannel datagramChannel = ((DatagramChannel) key.channel());

        PendingData pendingWrite = pendingWrites.get(datagramChannel);
        List<byte[]> pendingData = pendingWrite.getPendingData();

        for (byte[] data : pendingData)
            datagramChannel.send(ByteBuffer.wrap(data), datagramChannel.getRemoteAddress());

        pendingData.clear();
    }

    private void read(SelectionKey key) throws IOException {
        DatagramChannel datagramChannel = ((DatagramChannel) key.channel());
        ByteBuffer buffer = ByteBuffer.allocate(datagramBufferSize);

        datagramChannel.receive(buffer);
        buffer.flip();

        byte[] receivedData = new byte[buffer.limit()];
        buffer.get(receivedData);
        buffer.clear();

        byte[] replyData = createReplyData(receivedData);
        if (!pendingWrites.containsKey(datagramChannel))
            pendingWrites.put(datagramChannel, new PendingData());

        PendingData pendingData = pendingWrites.get(datagramChannel);
        pendingData.getPendingData().add(replyData);

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private class DiscoverableServerRunnable implements Runnable {
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
                            read(key);
                        } else if (key.isWritable()) {
                            write(key);
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

    private static InetAddress getLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.getDisplayName().contains("Virtual") || networkInterface.isVirtual()
                        || networkInterface.isLoopback() || !networkInterface.isUp()
                        || networkInterface.getDisplayName().contains("Tunneling"))
                    continue;

                List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                if (interfaceAddresses.size() > 0)
                    return interfaceAddresses.get(0).getAddress(); // could be an ipv4 or an ipv6 address
            }
            return null;
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
    }
}
