package com.bmdelacruz.socketify;

import com.bmdelacruz.socketify.client.Client;
import com.bmdelacruz.socketify.finder.ServerFinder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Scanner;

class SessionsServerTester implements Runnable {
    public static void main(String[] args) {
        new Thread(new SessionsServerTester()).start();
    }

    private Client client;

    @Override
    public void run() {
        String input = "";
        Scanner scanner = new Scanner(System.in);

        System.out.println("Type your command...");

        while (!input.equals("/exit") && scanner.hasNextLine()) {
            input = scanner.nextLine();
            processCommand(input);
        }

        System.out.println("Bye!");
    }

    private void processCommand(String input) {
        if (input.startsWith("/find")) {
            String[] args = input.split(" ");

            if (args.length < 3) {
                System.out.println("The 'find' command needs a port number and the data to send to the server.");
                System.out.println("Command: /find <port> <string data>");

                return;
            }

            String portStr = args[1];
            String dataStr = args[2];

            try {
                final int port = Integer.parseInt(portStr);
                final HashMap<String, byte[]> foundAddresses = new HashMap<>();

                ServerFinder serverFinder = new ServerFinder(2404, 256);
                serverFinder.setListener(new ServerFinder.Listener() {
                    @Override
                    public void onStart() {
                        System.out.println("Started finding servers at port " + port);
                    }

                    @Override
                    public void onFind(byte[] address, byte[] data) {
                        try {
                            String ip = InetAddress.getByAddress(address).getHostAddress();
                            foundAddresses.put(ip, address);
                        } catch (UnknownHostException ignored) { }
                    }

                    @Override
                    public void onFinish() {
                        if (foundAddresses.size() > 0) {
                            System.out.println("Found servers:");
                            for (String ip : foundAddresses.keySet()) {
                                System.out.println(">> " + ip);
                            }
                        } else {
                            System.out.println("No servers found.");
                        }

                    }
                });
                serverFinder.find(port, 1000, 5, dataStr.getBytes());
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number.\nCommand=\"" + input + "\"");
            } catch (IllegalStateException e) {
                System.out.println(e.getMessage() + "\nCommand=\"" + input + "\"");
            }
        } else if (input.startsWith("/connect")) {
            if (client != null) {
                System.out.println("You are already connected to a server.");
                return;
            }

            String[] args = input.split(" ");

            if (args.length < 3) {
                System.out.println("The 'connect' command needs an ip address and a port number of the target server.");
                System.out.println("Command: /connect <server_ip> <server_port>");
                return;
            }

            String targetIp = args[1];
            String targetPortStr = args[2];

            try {
                int targetPort = Integer.parseInt(targetPortStr);

                client = new Client(targetPort, targetIp, 512);
                client.setListener(new Client.Listener() {
                    @Override
                    public void onDataReceived(byte[] data) {
                        System.out.println("Received message from server:\n" + new String(data) + "\n");
                    }

                    @Override
                    public void onServerDisconnect() {
                        System.out.println("Server stopped.");

                        client = null;
                    }
                });
                client.connect();

                System.out.println("You are now connected to server (" + targetIp + ":" + targetPortStr + ")");
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number.\nCommand=\"" + input + "\"");
                client = null;
            } catch (IOException e) {
                System.out.println("Cannot connect to the server.\nCommand=\"" + input + "\"");
                client = null;
            }
        } else if (input.startsWith("/disconnect")) {
            if (client == null) {
                System.out.println("You are not connected to a server.");
                return;
            }

            try {
                client.disconnect();
                client = null;

                System.out.println("You are now disconnected from the server.");
            } catch (IOException ignored) { }
        } else if (input.startsWith("/send")) {
            if (client == null) {
                System.out.println("You are not connected to a server.");
                return;
            }

            String[] args = input.split(" ");

            if (args.length < 2) {
                System.out.println("The 'connect' command needs a data string to send to the server.");
                System.out.println("Command: /send <data_string>");
                return;
            }

            String dataStr = args[1];
            dataStr = dataStr.replace("\\n", "\n");

            try {
                client.sendBytes(dataStr.getBytes());

                System.out.println("Data was sent.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Command was not understood.");
        }
    }
}
