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

import com.picoff.journaldb.exception.DatabaseDirectoryIsAFileException;
import com.picoff.journaldb.exception.DatabaseLockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

public class JournalDB implements Closeable {
    private static final long RELOCATE_PARK_TIME_NS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final Logger LOGGER = LoggerFactory.getLogger(JournalDB.class);
    private final JournalDBOptions options;
    private final FileChannel metaChannel;
    private final FileLock metaLock;
    private final MappedByteBuffer metadata;
    private final AtomicReference<Journal> currentJournal = new AtomicReference<>();
    private final AtomicLong fileSequence = new AtomicLong(0);
    private final ReentrantLock metaWriteLock = new ReentrantLock();
    private final double maxJournalSizeBytes;
    private final Thread relocateMonitor;

    public JournalDB(final JournalDBOptions options) throws IOException {
        this.options = options;

        this.maxJournalSizeBytes = options.getJournalMaxSizeUnit().toBytes(options.getJournalMaxSize());

        final File dataDirectory = options.getDataDirectory();

        if (dataDirectory.exists() && dataDirectory.isFile()) {
            throw new DatabaseDirectoryIsAFileException();
        }

        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }

        final File metaFile = new File(dataDirectory, "journal_meta");
        final Path metaPath = metaFile.toPath();

        this.metaChannel = FileChannel.open(
            metaPath,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
            StandardOpenOption.CREATE
        );

        this.metaLock = this.metaChannel.tryLock();

        if (null == metaLock) {
            throw new DatabaseLockedException();
        }

        final boolean isNewDatabase = 0 == metaChannel.size();

        this.metadata = metaChannel.map(
            FileChannel.MapMode.READ_WRITE,
            0, 100
        );

        if (isNewDatabase) {
            currentJournal.set(createJournal());
        } else {
            loadPreviousJournal(options);
        }

        this.relocateMonitor = maxJournalSizeBytes != 0
            ? new Thread(this::relocateMonitorWork)
            : null;

        if (null != relocateMonitor) {
            relocateMonitor.setName("jdb-monitor");
            relocateMonitor.start();
        }
    }

    public void write(final byte[] bytes) throws IOException {
        if (maxJournalSizeBytes > 0 && currentJournal.get().size() > maxJournalSizeBytes) {
            LockSupport.unpark(relocateMonitor);
        }

        try {
            currentJournal.get().write(bytes);
        } catch (final IOException e) {
            onWriteFailure(e);
        }
    }

    private synchronized void onWriteFailure(final IOException e) throws IOException {
        if (options.relocateOnWriteFailure()) {
            LOGGER.warn("Write failure", e);
            relocate();
        } else {
            close();
            throw e;
        }
    }

    public long relocate() throws IOException {
        final long oldSequence = getSequence();
        final Journal oldJournal = currentJournal.get();
        currentJournal.set(createJournal());
        oldJournal.archiveAndClose();
        return oldSequence;
    }

    @Override
    public void close() throws IOException {
        try {
            if (relocateMonitor != null) {
                relocateMonitor.interrupt();
            }
        } finally {
            try {
                currentJournal.get().close();
            } finally {
                metaLock.close();
                metaChannel.close();
            }
        }
    }

    public long getSequence() {
        return fileSequence.get();
    }

    private Journal createJournal() throws IOException {
        final long fileSequence = allocateFileSequence();
        return new Journal(getJournalFile(fileSequence));
    }

    private long allocateFileSequence() {
        metaWriteLock.lock();
        try {
            final long nextSequence = fileSequence.getAndIncrement();
            metadata.putLong(0, nextSequence);
            return nextSequence;
        } finally {
            metaWriteLock.unlock();
        }
    }

    private File getJournalFile(final long fileSequence) {
        return new File(options.getDataDirectory(), generateJournalName(fileSequence));
    }

    private String generateJournalName(final long fileSequence) {
        return String.format("journal_%d.jdf", fileSequence);
    }

    public void write(final byte[] bytes, final boolean sync) throws IOException {
        if (maxJournalSizeBytes > 0 && currentJournal.get().size() > maxJournalSizeBytes) {
            LockSupport.unpark(relocateMonitor);
        }

        try {
            currentJournal.get().write(bytes, sync);
        } catch (final IOException e) {
            onWriteFailure(e);
        }
    }

    public void flush() {
        currentJournal.get().flush();
    }

    public JournalReader createReader(final long fileSequence) throws IOException {
        return new JournalReader(getJournalFile(fileSequence));
    }

    public JournalReader createReader(final JournalReaderOptions options, final long fileSequence) throws IOException {
        return new JournalReader(options, getJournalFile(fileSequence));
    }

    private void relocateMonitorWork() {
        if (0 == maxJournalSizeBytes) {
            return;
        }

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                if (currentJournal.get().size() > maxJournalSizeBytes) {
                    relocate();
                }
            } catch (final IOException e) {
                LOGGER.error("Failure in relocation monitor", e);
            }

            LockSupport.parkNanos(RELOCATE_PARK_TIME_NS);
        }
    }

    private void loadPreviousJournal(final JournalDBOptions options) throws IOException {
        final long sequence = metadata.getLong(0);
        fileSequence.set(sequence);
        try {
            currentJournal.set(openJournal(fileSequence.get()));
        } catch (final IOException e) {
            if (options.relocateOnBootFailure()) {
                LOGGER.warn("Failed to open previous journal, relocating to new journal", e);
                currentJournal.set(createJournal());
            } else {
                throw e;
            }
        }
    }

    private Journal openJournal(final long fileSequence) throws IOException {
        return new Journal(getJournalFile(fileSequence));
    }
}
