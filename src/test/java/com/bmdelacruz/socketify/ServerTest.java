package com.bmdelacruz.socketify;

import com.bmdelacruz.socketify.finder.FindableServer;
import com.bmdelacruz.socketify.server.ClientConnection;
import com.bmdelacruz.socketify.server.Server;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) {
        try {
            FindableServer server = new FindableServer(10849, 10850, 512, 512);
            server.setListener(new Server.Listener() {
                @Override
                public void onClientConnect(ClientConnection clientConnection) {
                    System.out.println("Client connected.");
                    clientConnection.addListener(new ClientConnection.Listener() {
                        @Override
                        public void onDataReceived(ClientConnection clientConnection, byte[] data, ClientConnection.Messenger messenger) {
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
                        public void onDisconnected(ClientConnection clientConnection) {
                            System.out.println("Client disconnected.");
                        }

                        @Override
                        public void onFailure(ClientConnection clientConnection) {
                            System.out.println("Client connection stopped unexpectedly.");
                        }
                    });
                }
            });
            server.setListener(new FindableServer.Listener() {
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
