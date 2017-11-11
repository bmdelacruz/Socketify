package com.bmdelacruz.socketify.finder;

import com.bmdelacruz.socketify.commons.Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class ServerFinder {
    private final int port;
    private final int bufferSize;
    private final InetAddress broadcastAddress;

    private Listener listener;

    private volatile boolean isFinding;
    private Thread serverFinderThread;

    public interface Listener {
        void onStart();
        void onFind(byte[] address, byte[] data);
        void onFinish();
    }

    public ServerFinder(int port, int bufferSize) {
        this.port = port;
        this.bufferSize = bufferSize;

        InetAddress localAddress = Utils.getLocalAddress();
        broadcastAddress = Utils.getBroadcastAddressOf(localAddress);

        if (localAddress == null || broadcastAddress == null) {
            throw new IllegalStateException("No valid local or broadcast address found.");
        }
    }

    /**
     * Check whether this instance of ServerFinder is still looking for servers.
     */
    public boolean isFinding() {
        return isFinding;
    }

    public void setListener(Listener listener) {
        if (!isFinding)
            this.listener = listener;
    }

    /**
     * Starts finding servers in the local network.
     * @param port The port of the server to which the data can be sent.
     * @param duration The duration to wait for this to receive the server's reply.
     * @param repeats Number of times this must send out server requests.
     * @param data The data to be sent to the server during discovery.
     */
    public void find(final int port, final int duration, final int repeats, final byte[] data) {
        serverFinderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                isFinding = true;

                try {
                    DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddress, port);
                    final DatagramSocket socket = new DatagramSocket(ServerFinder.this.port);
                    socket.setBroadcast(true);
                    socket.setSoTimeout(1500);

                    Thread readThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null)
                                listener.onStart();

                            byte[] receivedPacketBuffer = new byte[bufferSize];
                            DatagramPacket receivedPacket = new DatagramPacket(
                                    receivedPacketBuffer, receivedPacketBuffer.length);

                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    socket.receive(receivedPacket);

                                    byte[] address = receivedPacket.getAddress().getAddress();
                                    byte[] data = receivedPacket.getData();

                                    if (listener != null)
                                        listener.onFind(address, data);
                                } catch (SocketTimeoutException ignored) {
                                } catch (IOException e) {
                                    break;
                                }
                            }

                            socket.close();
                        }
                    });
                    readThread.start();

                    for (int i = 0; i < repeats && !Thread.currentThread().isInterrupted()
                            && !readThread.isInterrupted(); i++) {
                        socket.send(packet);

                        try { Thread.sleep(duration); } catch (InterruptedException ignored) { }
                    }

                    readThread.interrupt();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                isFinding = false;
                if (listener != null)
                    listener.onFinish();
            }
        });
        serverFinderThread.start();
    }

    /**
     * Stop looking for servers.
     */
    public void stop() {
        if (isFinding) {
            serverFinderThread.interrupt();
        }
    }
}
