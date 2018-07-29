package com.picoff.journaldb;

import java.io.IOException;

public class JournalEntry {
    private boolean integrityPass;
    private int dataSize;
    private long sequence;
    private long timestamp;
    private byte[] data;
    private long checksum;
    private boolean magicBytePass;
    private EntryProcessedStateWriter processedStateWriter;
    private boolean processed;
    private Long processedTimestamp;

    public Long getProcessedTimestamp() {
        return processedTimestamp;
    }

    void setProcessedTimestamp(final Long processedTimestamp) {
        this.processedTimestamp = processedTimestamp;
    }

    public boolean getProcessed() {
        return processed;
    }

    void setProcessed(final boolean processed) {
        this.processed = processed;
    }

    public boolean isIntegrityPass() {
        return integrityPass;
    }

    void setIntegrityPass(final boolean integrityPass) {
        this.integrityPass = integrityPass;
    }

    public int getDataSize() {
        return dataSize;
    }

    void setDataSize(final int dataSize) {
        this.dataSize = dataSize;
    }

    public long getSequence() {
        return sequence;
    }

    void setSequence(final long sequence) {
        this.sequence = sequence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getData() {
        return data;
    }

    void setData(final byte[] data) {
        this.data = data;
    }

    public long getChecksum() {
        return checksum;
    }

    void setChecksum(final long checksum) {
        this.checksum = checksum;
    }

    public boolean isMagicBytePass() {
        return magicBytePass;
    }

    void setMagicBytePass(final boolean magicBytePass) {
        this.magicBytePass = magicBytePass;
    }

    public void writeProcessedState(final boolean state, final boolean sync) throws IOException {
        processedStateWriter.writeState(state, sync);
    }

    void setProcessedStateWriter(final EntryProcessedStateWriter processedStateWriter) {
        this.processedStateWriter = processedStateWriter;
    }
}
