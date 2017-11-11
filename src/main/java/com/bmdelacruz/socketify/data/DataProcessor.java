package com.bmdelacruz.socketify.data;

public abstract class DataProcessor {
    private DataProcessor next;

    public abstract byte[] process(byte[] data);

    public final DataProcessor getNext() {
        return next;
    }

    public final void setNext(DataProcessor dataProcessor) {
        next = dataProcessor;
    }
}
