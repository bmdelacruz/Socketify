package com.bmdelacruz.connlib;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) {
        try {
            Server server = new Server(10849, 512);
            server.addListener(new Server.Listener() {
                @Override
                public void onClientConnect(ClientConnection clientConnection) {
                    System.out.println("Client connected.");
                    clientConnection.addListener(new ClientConnection.Listener() {
                        @Override
                        public void onDataReceived(byte[] data, ClientConnection.Messenger messenger) {
                            System.out.println("Received data from client:\n" + new String(data) + "\n");

                            messenger.reply(data);
                        }

                        @Override
                        public void onDisconnected() {
                            System.out.println("Client disconnected.");
                        }

                        @Override
                        public void onFailure(Exception e) {
                            System.out.println("Client connection stopped unexpectedly.");
                        }
                    });
                }

                @Override
                public void onClientMessageFailed(ClientConnection clientConnection, Exception e) {
                    System.out.println("Cannot send message to client.");
                }
            });

            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
