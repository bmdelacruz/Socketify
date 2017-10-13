package com.bmdelacruz.connlib;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) {
        try {
            FindableServer server = new FindableServer(10849, 10850, 512, 512);
            server.addListener(new Server.Listener() {
                @Override
                public void onClientConnect(ClientConnection clientConnection) {
                    System.out.println("Client connected.");
                    clientConnection.addListener(new ClientConnection.Listener() {
                        @Override
                        public void onDataReceived(byte[] data, ClientConnection.Messenger messenger) {
                            String dataStr = new String(data);
                            System.out.println("Received data from client:\n" + dataStr + "\n");

                            switch (dataStr) {
                                case "1":
                                    messenger.reply("I like the number 1! <3".getBytes());
                                    break;
                                default:
                                    messenger.reply(data);
                                    break;
                            }
                        }

                        @Override
                        public void onDisconnected() {
                            System.out.println("Client disconnected.");
                        }

                        @Override
                        public void onFailure() {
                            System.out.println("Client connection stopped unexpectedly.");
                        }
                    });
                }

                @Override
                public void onClientMessageFailed(ClientConnection clientConnection, Exception e) {
                    System.out.println("Cannot send message to client.");
                }
            });
            server.addListener(new FindableServer.Listener() {
                @Override
                public void onFound() {
                    System.out.println("Server got discovered.");
                }
            });

            server.start();
            server.startDiscoverability();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
