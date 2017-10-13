package com.bmdelacruz.connlib;

import java.nio.ByteBuffer;
import java.util.List;

class Utils {
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

    static byte[] extractBytesFrom(ByteBuffer byteBuffer) {
        byte[] newData = new byte[byteBuffer.limit()];
        byteBuffer.get(newData);

        return newData;
    }
}
