/*
 * Copyright 2018 Picoff Ventures and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.picoff.journaldb;

import com.picoff.commons.unit.DigitalUnit;

import java.io.File;

public class JournalDBOptions {
    private File dataDirectory;
    private long journalMaxSize = 1;
    private DigitalUnit journalMaxSizeUnit = DigitalUnit.GIGABYTE;
    private boolean relocateOnBootFailure = false;
    private boolean relocateOnWriteFailure = false;

    public File getDataDirectory() {
        return dataDirectory;
    }

    public JournalDBOptions setDataDirectory(final File dataDirectory) {
        this.dataDirectory = dataDirectory;
        return this;
    }

    public long getJournalMaxSize() {
        return journalMaxSize;
    }

    public DigitalUnit getJournalMaxSizeUnit() {
        return journalMaxSizeUnit;
    }

    public JournalDBOptions setJournalMaxSize(final long journalMaxSize, final DigitalUnit unit) {
        this.journalMaxSize = journalMaxSize;
        this.journalMaxSizeUnit = unit;
        return this;
    }

    public JournalDBOptions setRelocateOnBootFailure(final boolean relocateOnBootFailure) {
        this.relocateOnBootFailure = relocateOnBootFailure;
        return this;
    }

    public boolean relocateOnBootFailure() {
        return relocateOnBootFailure;
    }

    public boolean relocateOnWriteFailure() {
        return relocateOnWriteFailure;
    }

    public JournalDBOptions setRelocateOnWriteFailure(final boolean relocateOnWriteFailure) {
        this.relocateOnWriteFailure = relocateOnWriteFailure;
        return this;
    }
}
