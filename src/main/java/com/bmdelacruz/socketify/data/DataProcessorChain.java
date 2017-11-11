package com.bmdelacruz.socketify.data;

public class DataProcessorChain {
    private DataProcessor first;
    private DataProcessor last;

    public final void addDataProcessor(DataProcessor dataProcessor) {
        if (dataProcessor == null)
            throw new IllegalArgumentException("The dataProcessor cannot be null.");
        if (first == null)
            first = dataProcessor;
        if (last != null)
            last.setNext(dataProcessor);
        last = dataProcessor;
    }

    public byte[] process(byte[] data) {
        DataProcessor temp = first;
        while (temp != null) {
            data = temp.process(data);
            temp = temp.getNext();
        }
        return data;
    }
}
