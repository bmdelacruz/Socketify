package com.bmdelacruz.connlib;

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
    }

    public boolean isFinding() {
        return isFinding;
    }

    public void setListener(Listener listener) {
        if (!isFinding)
            this.listener = listener;
    }

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
                            listener.onStart();

                            byte[] receivedPacketBuffer = new byte[bufferSize];
                            DatagramPacket receivedPacket = new DatagramPacket(
                                    receivedPacketBuffer, receivedPacketBuffer.length);

                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    socket.receive(receivedPacket);

                                    byte[] address = receivedPacket.getAddress().getAddress();
                                    byte[] data = receivedPacket.getData();

                                    listener.onFind(address, data);
                                } catch (SocketTimeoutException ignored) {
                                } catch (IOException e) {
                                    break;
                                }
                            }
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
                listener.onFinish();
            }
        });
        serverFinderThread.start();
    }

    public void stop() {
        if (isFinding) {
            serverFinderThread.interrupt();
        }
    }
}
