package com.picoff.journaldb.exception;

public class RecordChecksumException extends RecordReadException {
    public RecordChecksumException(final long filePointer) {
        super(filePointer);
    }
}
