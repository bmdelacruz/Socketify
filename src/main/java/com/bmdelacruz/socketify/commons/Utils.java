package com.bmdelacruz.socketify.commons;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class Utils {
    /**
     * Concatenate the contents of the byte arrays on the list.
     * @param arrays The byte arrays to concatenate.
     * @return A new concatenated byte array.
     */
    public static byte[] concatenate(List<byte[]> arrays) {
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
    public static byte[] extractBytesFrom(ByteBuffer byteBuffer) {
        byte[] newData = new byte[byteBuffer.limit()];
        byteBuffer.get(newData);

        return newData;
    }

    /**
     * Get the local network address of the device. This may return an address of a
     * network interface which is either an ethernet or a wifi device. For the address
     * to be selected, it must be up, not loopback, not virtual, not a link local, and
     * a loopback address. If none met the criteria, null will be returned.
     *
     * @return The local address of the device.
     */
    public static InetAddress getLocalAddress() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                if (n.getName().contains("eth") || n.getName().contains("wlan")
                        || n.getName().contains("net") || n.getName().contains("ap"))
                    if (n.isUp() && !n.isLoopback() && !n.isVirtual())
                        for (InetAddress a : Collections.list(n.getInetAddresses()))
                            if (!a.isLinkLocalAddress() && !a.isLoopbackAddress())
                                if (getBroadcastAddressOf(a) != null)
                                    return a;
            return null;
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static InetAddress getBroadcastAddressOf(InetAddress address) {
        try {
            NetworkInterface i = NetworkInterface.getByInetAddress(address);
            for (InterfaceAddress ia : i.getInterfaceAddresses()) {
                if (ia.getBroadcast() != null)
                    return ia.getBroadcast();
            }
            return null;
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
    }
}
