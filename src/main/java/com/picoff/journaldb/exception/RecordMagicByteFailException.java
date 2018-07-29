package com.picoff.journaldb.exception;

public class RecordMagicByteFailException extends RecordReadException {
    public RecordMagicByteFailException(final long filePointer) {
        super(filePointer);
    }
}
