package com.bmdelacruz.connlib.server;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public class ClientConnection {
    private SocketChannel socketChannel;
    private ArrayList<Listener> listeners;

    public interface Listener {
        void onDataReceived(byte[] data, Messenger messenger);
        void onDisconnected();
        void onFailure();
    }

    public interface Messenger {
        void reply(byte[] data);
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
        for (Listener listener : listeners) listener.onDataReceived(data, messenger);
    }

    public void onDisconnected() {
        for (Listener listener : listeners) listener.onDisconnected();
    }

    public void onFailure() {
        for (Listener listener : listeners) listener.onFailure();
    }
}
