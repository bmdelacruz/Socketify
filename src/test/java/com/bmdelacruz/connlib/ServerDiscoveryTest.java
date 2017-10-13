package com.bmdelacruz.connlib;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Iterator;

public class ServerDiscoveryTest {
    public static void main(String[] args) throws IOException {
        final HashMap<String, byte[]> addresses = new HashMap<>();

        ServerFinder serverFinder = new ServerFinder(10851, 1024);
        serverFinder.setListener(new ServerFinder.Listener() {
            @Override
            public void onStart() {
                System.out.println("Started finding servers.");
            }

            @Override
            public void onFind(byte[] address, byte[] data) {
                try {
                    System.out.println("Found a server at "
                            + InetAddress.getByAddress(address).getHostAddress()
                            + " with data:\n" + new String(data));

                    addresses.put(InetAddress.getByAddress(address).getHostAddress(), address);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFinish() {
                System.out.println("Finished finding servers.");

                if (!addresses.isEmpty()) {
                    Iterator<byte[]> iterator = addresses.values().iterator();
                    if (iterator.hasNext()) {
                        try {
                            String serverAddress = InetAddress.getByAddress(iterator.next()).getHostAddress();
                            Client client = new Client(10849, serverAddress);
                            client.addListener(new Client.Listener() {
                                @Override
                                public void onDataReceived(byte[] data) {
                                    System.out.println("Received message from server:\n" + new String(data) + "\n");
                                }

                                @Override
                                public void onServerDisconnect() {
                                    System.out.println("Server stopped.");
                                }
                            });

                            client.connect();

                            System.out.println("Connected to " + serverAddress);

                            for (int i = 0; i < 5 && client.isConnected(); i++) {
                                client.sendBytes(Integer.toString(i).getBytes());
                                try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
                            }

                            client.disconnect();

                            System.out.println("Disconnected from " + serverAddress);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        serverFinder.find(10850, 3000, 5, "FIND".getBytes());
    }
}
