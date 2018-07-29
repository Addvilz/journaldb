package com.picoff.journaldb;

import java.util.function.Predicate;

public class EntryReadOptions {
    private long startPosition = Journal.FILE_HEADER_SIZE;
    private boolean failOnMagicByte = true;
    private boolean failOnIntegrityByte = true;
    private boolean verifyChecksum = true;
    private Predicate<FilterMetadata> readFilter = null;

    public long getStartPosition() {
        return startPosition;
    }

    public EntryReadOptions setStartPosition(final long startPosition) {
        this.startPosition = startPosition;
        return this;
    }

    public boolean failOnMagicByte() {
        return failOnMagicByte;
    }

    public EntryReadOptions setFailOnMagicByte(final boolean failOnMagicByte) {
        this.failOnMagicByte = failOnMagicByte;
        return this;
    }

    public boolean failOnIntegrityByte() {
        return failOnIntegrityByte;
    }

    public EntryReadOptions setFailOnIntegrityByte(final boolean failOnIntegrityByte) {
        this.failOnIntegrityByte = failOnIntegrityByte;
        return this;
    }

    public boolean verifyChecksum() {
        return verifyChecksum;
    }

    public EntryReadOptions setVerifyChecksum(final boolean verifyChecksum) {
        this.verifyChecksum = verifyChecksum;
        return this;
    }

    public Predicate<FilterMetadata> getReadFilter() {
        return readFilter;
    }

    public EntryReadOptions setReadFilter(final Predicate<FilterMetadata> readFilter) {
        this.readFilter = readFilter;
        return this;
    }
}
