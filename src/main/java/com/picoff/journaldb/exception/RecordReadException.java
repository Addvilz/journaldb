package com.picoff.journaldb.exception;

import java.io.IOException;

public class RecordReadException extends IOException {
    private final long filePointer;

    public RecordReadException(final long filePointer) {
        this.filePointer = filePointer;
    }

    public long getFilePointer() {
        return filePointer;
    }
}
