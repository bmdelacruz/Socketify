package com.bmdelacruz.socketify.commons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PendingData {
    private volatile boolean hasPendingData;
    private List<byte[]> pendingData;

    public PendingData() {
        this.pendingData = Collections.synchronizedList(new ArrayList<byte[]>());
        this.hasPendingData = false;
    }

    public List<byte[]> getPendingData() {
        return pendingData;
    }

    public boolean hasPendingData() {
        return hasPendingData;
    }

    public void setHasPendingData(boolean hasPendingData) {
        this.hasPendingData = hasPendingData;
    }
}
