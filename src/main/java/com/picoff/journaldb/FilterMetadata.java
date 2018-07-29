package com.picoff.journaldb;

public class FilterMetadata {
    private final boolean integrityFlag;
    private final boolean isProcessed;
    private final long sequence;
    private final long timestamp;
    private final long processedTimestamp;

    FilterMetadata(
        final boolean integrityFlag,
        final boolean isProcessed,
        final long processedTimestamp,
        final long sequence,
        final long timestamp
    ) {
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.isProcessed = isProcessed;
        this.processedTimestamp = processedTimestamp;
        this.integrityFlag = integrityFlag;
    }

    public boolean getIntegrityFlag() {
        return integrityFlag;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public long getProcessedTimestamp() {
        return processedTimestamp;
    }

    public long getSequence() {
        return sequence;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
