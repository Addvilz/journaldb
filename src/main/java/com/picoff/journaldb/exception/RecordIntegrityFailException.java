package com.picoff.journaldb.exception;

public class RecordIntegrityFailException extends RecordReadException {
    public RecordIntegrityFailException(final long filePointer) {
        super(filePointer);
    }
}
