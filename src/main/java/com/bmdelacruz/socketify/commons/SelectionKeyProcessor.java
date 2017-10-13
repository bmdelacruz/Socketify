package com.bmdelacruz.socketify.commons;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public abstract class SelectionKeyProcessor {
    private final List<byte[]> completeDataList;
    private final int bufferSize;

    public SelectionKeyProcessor() {
        this(512);
    }

    public SelectionKeyProcessor(int bufferSize) {
        this.bufferSize = bufferSize;
        this.completeDataList = new ArrayList<>();
    }

    /**
     * @return The marker that signifies the end of the data.
     */
    public byte getMarkerByte() {
        return (byte) 0x00;
    }

    /**
     * Retriever of the pending reads for the passed key.
     * @param key The basis selection key.
     * @return The pending data list for the key.
     */
    public abstract List<byte[]> getPendingReadList(SelectionKey key);

    /**
     * Process the complete data from the passed key.
     * @param key The basis selection key.
     * @param data The complete data from the key.
     */
    public abstract void processCompleteData(SelectionKey key, byte[] data);

    /**
     * For when the connection fails...
     * @param key The key of the connection that failed.
     */
    public abstract void onConnectionFailure(SelectionKey key);

    /**
     * For when the connection stops...
     * @param key The key of the connection that stopped.
     */
    public abstract void onDisconnect(SelectionKey key);

    /**
     * Read the data from the key.
     * @param key The key which will be read.
     */
    public final void read(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        try {
            int numOfReadBytes = socketChannel.read(buffer);
            buffer.flip();

            if (numOfReadBytes == -1) {
                onDisconnect(key);
            } else {
                boolean isNullByteFound = false;
                boolean hasCompleteMessage = false;
                ByteBuffer remainingDataBuffer = null;

                List<byte[]> pendingDataList = getPendingReadList(key);
                if (pendingDataList == null) {
                    throw new IllegalArgumentException("The pendingDataList cannot be null.");
                }

                while (buffer.hasRemaining() && !isNullByteFound) {
                    isNullByteFound = hasCompleteMessage = buffer.get() == getMarkerByte();
                    if (isNullByteFound) {
                        // CASE 1: Data is complete.
                        // The end of the data is on the same buffer.

                        remainingDataBuffer = buffer.slice();
                        buffer.reset().flip();

                        byte[] newData = Utils.extractBytesFrom(buffer);
                        if (pendingDataList.size() > 0) {
                            pendingDataList.add(newData);
                            newData = Utils.concatenate(pendingDataList);
                            pendingDataList.clear();
                        }

                        completeDataList.add(newData);
                    } else {
                        buffer.mark();
                    }
                }

                if (hasCompleteMessage) {
                    // Loop on the buffer of the remaining data to check whether
                    // there are more complete data.

                    while (remainingDataBuffer != null && remainingDataBuffer.hasRemaining()) {
                        boolean isRemNullByteFound = false;
                        while (remainingDataBuffer.hasRemaining()) {
                            isRemNullByteFound = remainingDataBuffer.get() == getMarkerByte();
                            if (isRemNullByteFound) {
                                // CASE 3: Extra data was found.
                                // The end of the pending data or the first data was already
                                // found but another complete data was found.

                                ByteBuffer temp = remainingDataBuffer.slice();
                                remainingDataBuffer.reset().flip();

                                completeDataList.add(Utils.extractBytesFrom(remainingDataBuffer));

                                remainingDataBuffer = temp;
                            } else {
                                remainingDataBuffer.mark();
                            }
                        }

                        if (!isRemNullByteFound) {
                            // CASE 4: Extra data is incomplete
                            // The end of the extra data is not on the buffer but the end
                            // of the first data was already found.

                            remainingDataBuffer.flip();
                            pendingDataList.add(Utils.extractBytesFrom(remainingDataBuffer));
                        }
                    }

                    for (byte[] data : completeDataList) {
                        processCompleteData(key, data);
                    }
                    completeDataList.clear();
                } else {
                    // CASE 2: Data is incomplete.
                    // The end of the data is on a different key.

                    buffer.flip();
                    pendingDataList.add(Utils.extractBytesFrom(buffer));
                }
            }
        } catch (IOException e) {
            onConnectionFailure(key);
        }
    }
}
