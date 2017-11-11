package com.bmdelacruz.socketify.server;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public class ClientConnection {
    private SocketChannel socketChannel;
    private ArrayList<Listener> listeners;

    public interface Listener {
        void onDataReceived(ClientConnection clientConnection, byte[] data, Messenger messenger);
        void onDisconnected(ClientConnection clientConnection);
        void onFailure(ClientConnection clientConnection);
    }

    public interface Messenger {
        void reply(byte[] data);
        void multicast(byte[] data, Server.MulticastCondition multicastCondition);
        void broadcast(byte[] data);
    }

    public ClientConnection(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.listeners = new ArrayList<>();
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public void onDataReceived(byte[] data, Messenger messenger) {
        for (Listener listener : listeners) listener.onDataReceived(this, data, messenger);
    }

    public void onDisconnected() {
        for (Listener listener : listeners) listener.onDisconnected(this);
    }

    public void onFailure() {
        for (Listener listener : listeners) listener.onFailure(this);
    }
}
