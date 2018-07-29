package com.picoff.journaldb;

import java.io.IOException;

@FunctionalInterface
public interface EntryProcessedStateWriter {
    void writeState(final boolean state, final boolean sync) throws IOException;
}
