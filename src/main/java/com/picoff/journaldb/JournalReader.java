package com.picoff.journaldb;

import com.picoff.commons.functional.Handler;
import com.picoff.journaldb.exception.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.zip.CRC32;

public class JournalReader implements Closeable {
    private static final EntryReadOptions DEFAULT_READ_OPTIONS = new EntryReadOptions();
    private static final JournalReaderOptions DEFAULT_OPEN_OPTIONS = new JournalReaderOptions();
    private final RandomAccessFile randomAccessFile;
    private final JournalMetadata metadata;
    private final File file;
    private final Path path;

    public JournalReader(final File file) throws IOException {
        this(DEFAULT_OPEN_OPTIONS, file);
    }

    public JournalReader(final JournalReaderOptions options, final File file) throws IOException {
        this.file = file.getAbsoluteFile();
        this.path = this.file.toPath();
        this.randomAccessFile = new RandomAccessFile(this.file, "r");

        randomAccessFile.seek(0);

        final byte magicByte = randomAccessFile.readByte();

        if (options.failOnMagicByte() && magicByte != Journal.FILE_MAGIC_BYTE) {
            throw new JournalOpenSignatureException();
        }

        final byte isArchived = randomAccessFile.readByte();

        if (options.failOnNotArchived() && Journal.B_TRUE == isArchived) {
            throw new JournalNotArchivedException();
        }

        final byte isClosedGracefully = randomAccessFile.readByte();

        if (options.failOnNotClosedGracefully() && Journal.B_FALSE == isClosedGracefully) {
            throw new JournalNotClosedGracefullyException();
        }

        final long createdAt = randomAccessFile.readLong();
        final long archivedAt = randomAccessFile.readLong();
        final long sequence = randomAccessFile.readLong();
        final long position = randomAccessFile.readLong();

        this.metadata = new JournalMetadata(
            Journal.B_TRUE == isArchived,
            Journal.B_TRUE == isClosedGracefully,
            createdAt,
            archivedAt,
            sequence,
            position
        );

    }

    public JournalMetadata getMetadata() {
        return metadata;
    }

    public void forEachEntry(final Handler<JournalEntry> entryHandler) throws IOException {
        forEachEntry(DEFAULT_READ_OPTIONS, entryHandler);
    }

    public void forEachEntry(
        final EntryReadOptions options,
        final Handler<JournalEntry> entryHandler
    ) throws IOException {
        randomAccessFile.seek(options.getStartPosition());

        while (randomAccessFile.getFilePointer() < randomAccessFile.length()) {
            final long recordStartPosition = randomAccessFile.getFilePointer();

            final byte magicByte = randomAccessFile.readByte();

            if (options.failOnMagicByte() && Journal.RECORD_MAGIC_BYTE != magicByte) {
                throw new RecordMagicByteFailException(randomAccessFile.getFilePointer());
            }

            final byte integrityFlag = randomAccessFile.readByte();

            if (options.failOnIntegrityByte() && Journal.B_TRUE != integrityFlag) {
                throw new RecordIntegrityFailException(randomAccessFile.getFilePointer());
            }

            final int dataSize = randomAccessFile.readInt();
            final long sequence = randomAccessFile.readLong();
            final long timestamp = randomAccessFile.readLong();
            final boolean isProcessed = randomAccessFile.readByte() == Journal.B_TRUE;
            final long processedTimestamp = randomAccessFile.readLong();

            final boolean filterPass = options.getReadFilter() == null || options
                .getReadFilter()
                .test(new FilterMetadata(
                    integrityFlag == Journal.B_TRUE, isProcessed, processedTimestamp, sequence,
                    timestamp
                ));

            if (!filterPass) {
                final long nextRecordPosition = recordStartPosition + dataSize + Journal.RECORD_HEADER_SIZE + Journal.RECORD_CHECKSUM_SIZE;
                randomAccessFile.seek(nextRecordPosition);
                continue;
            }

            randomAccessFile.skipBytes(17);

            final byte[] data = new byte[dataSize];
            randomAccessFile.readFully(data);

            final long checksum = randomAccessFile.readLong();

            if (options.verifyChecksum()) {
                final CRC32 crc32 = new CRC32();
                crc32.update(data);

                if (crc32.getValue() != checksum) {
                    throw new RecordChecksumException(randomAccessFile.getFilePointer());
                }
            }

            final JournalEntry entry = new JournalEntry();

            entry.setMagicBytePass(magicByte == Journal.RECORD_MAGIC_BYTE);
            entry.setIntegrityPass(integrityFlag == Journal.B_TRUE);
            entry.setDataSize(dataSize);
            entry.setSequence(sequence);
            entry.setTimestamp(timestamp);
            entry.setData(data);
            entry.setChecksum(checksum);
            entry.setProcessed(isProcessed);
            entry.setProcessedTimestamp(processedTimestamp);
            entry.setProcessedStateWriter((state, sync) -> {
                final long newProcessed = System.currentTimeMillis();
                entry.setProcessed(state);
                entry.setProcessedTimestamp(state ? newProcessed : null);
                Journal.markRecordProcessed(recordStartPosition, state, sync, path, newProcessed);
            });

            entryHandler.handle(entry);
        }
    }

    @Override
    public void close() throws IOException {
        randomAccessFile.close();
    }
}
