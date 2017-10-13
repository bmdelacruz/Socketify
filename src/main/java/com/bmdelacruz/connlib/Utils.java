package com.bmdelacruz.connlib;

import java.nio.ByteBuffer;
import java.util.List;

class Utils {
    /**
     * Concatenate the contents of the byte arrays on the list.
     * @param arrays The byte arrays to concatenate.
     * @return A new concatenated byte array.
     */
    static byte[] concatenate(List<byte[]> arrays) {
        int totalBytes = 0;
        for (byte[] bytes : arrays)
            totalBytes += bytes.length;

        int offset = 0;
        byte[] completeData = new byte[totalBytes];
        for (byte[] bytes : arrays) {
            System.arraycopy(bytes, 0, completeData, offset, bytes.length);
            offset += bytes.length;
        }

        return completeData;
    }

    /**
     * Extracts the bytes from the ByteBuffer object. The size of the
     * byte array is equal to the limit of the ByteBuffer object.
     * @param byteBuffer The buffer from which the data will be retrieved from.
     * @return A new byte array which contains the data on the ByteBuffer object.
     */
    static byte[] extractBytesFrom(ByteBuffer byteBuffer) {
        byte[] newData = new byte[byteBuffer.limit()];
        byteBuffer.get(newData);

        return newData;
    }
}
