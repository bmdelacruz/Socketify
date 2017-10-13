package com.bmdelacruz.connlib;

import java.io.IOException;

public class ClientTest {

    private static final String LOREM_IPSUM_1024 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut pharetra urna et " +
            "diam condimentum, commodo vulputate sapien interdum. Donec id vehicula arcu. Donec hendrerit leo id ante condimentum" +
            " fermentum. Cras congue blandit lorem a fermentum. Nulla malesuada, metus eget egestas facilisis, mi tellus facilisi" +
            "s nisi, sit amet blandit lacus mi at velit. Nullam dignissim diam in elit ultricies mattis. Integer eu dui elit. Ali" +
            "quam maximus ac mi ut ultrices. In in mauris ac nisi tincidunt semper. Phasellus vestibulum lobortis tellus, ac impe" +
            "rdiet dui condimentum sed. Proin tempor sem ac eleifend bibendum. Donec maximus eget nunc in ultrices. Donec loborti" +
            "s neque nulla, sed rutrum odio blandit in. Morbi elit mauris, feugiat vitae ligula et, molestie fringilla nisi. Morb" +
            "i at lacus bibendum, porta est nec, tempor augue. In sed tortor enim. Mauris magna magna, porta id tristique vitae, " +
            "varius vitae ligula. In lectus libero, interdum ut justo a, ullamcorper aliquet ipsum. Cras non aliquam orci. In ege" +
            "stas magna a nullam.";
    private static final String LOREM_IPSUM_EX = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut pharetra urna et " +
            "diam condimentum, commodo vulputate sapien interdum. Donec id vehicula arcu. Donec hendrerit leo id ante condimentum" +
            " fermentum. Cras congue blandit lorem a fermentum. Nulla malesuada, metus eget egestas facilisis, mi tellus facilisi" +
            "s nisi, sit amet blandit lacus mi at velit. Nullam dignissim diam in elit ultricies mattis. Integer eu dui elit. Ali" +
            "quam maximus ac mi ut ultrices. In in mauris ac nisi tincidunt semper. Phasellus vestibulum lobortis tellus, ac impe" +
            "rdiet dui condimentum sed. Proin tempor sem ac eleifend bibendum. Donec maximus eget nunc in ultrices. Donec loborti" +
            "s neque nulla, sed rutrum odio blandit in. Morbi elit mauris, feugiat vitae ligula et, molestie fringilla nisi. Morb" +
            "i at lacus bibendum, porta est nec, tempor augue. In sed tortor enim. Mauris magna magna, porta id tristique vitae, " +
            "varius vitae ligula. In lectus libero, interdum ut justo a, ullamcorper aliquet ipsum. Cras non aliquam orci. In ege" +
            "stas magna a nullam. NOOT.";
    private static final String NUMBERS = "1234567890";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static void main(String[] args) {
        try {
            Client client = new Client(10849);
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

            client.sendBytes(LOREM_IPSUM_1024.getBytes());
            client.sendBytes(LOREM_IPSUM_EX.getBytes());
            client.sendBytes(NUMBERS.getBytes());
            client.sendBytes(LETTERS.getBytes());

            client.sendBytes("1".getBytes());
            client.sendBytes("2".getBytes());
            client.sendBytes("3".getBytes());
            client.sendBytes("4".getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
