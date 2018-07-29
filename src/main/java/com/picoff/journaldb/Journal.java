package com.picoff.journaldb;

import com.picoff.journaldb.exception.ArchivedJournalWriteException;
import com.picoff.journaldb.exception.JournalMagicByteException;
import com.picoff.journaldb.exception.NotClosedGracefullyException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;


public class Journal implements Closeable {
    public static final int FILE_HEADER_SIZE = 100;
    public static final int RECORD_HEADER_SIZE = 48;
    public static final int RECORD_CHECKSUM_SIZE = 8;
    public static final byte FILE_MAGIC_BYTE = (byte) 'j';
    public static final byte RECORD_MAGIC_BYTE = (byte) 'r';
    public static final byte B_TRUE = (byte) 1;
    public static final byte B_FALSE = (byte) 0;
    private static final byte[] RECORD_WRITE_CONFIRM_FLAG = {B_TRUE};

    private final ReentrantLock allocationLock = new ReentrantLock();
    private final File file;
    private final FileChannel headerChannel;
    private final AtomicLong positionIndicator = new AtomicLong(FILE_HEADER_SIZE);
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final MappedByteBuffer header;
    private final Path path;

    public Journal(
        final File file
    ) throws IOException {
        this.file = file.getAbsoluteFile();
        this.path = file.getAbsoluteFile().toPath();

        this.headerChannel = FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.DSYNC
        );

        final boolean isNew = 0 == headerChannel.size();

        this.header = headerChannel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_HEADER_SIZE);

        if (isNew) {
            header.put(FILE_MAGIC_BYTE);
            header.put(B_FALSE); // Is archived
            header.put(B_FALSE); // Is closed gracefully
            header.putLong(System.currentTimeMillis()); // Created at
            header.putLong(0L); // Archived at
            header.putLong(0L); // Sequence counter
            header.putLong(FILE_HEADER_SIZE); // File end position
        } else {
            header.position(0);

            final byte magicByte = header.get();

            if (FILE_MAGIC_BYTE != magicByte) {
                throw new JournalMagicByteException();
            }

            final byte isArchived = header.get();

            if (B_TRUE == isArchived) {
                throw new ArchivedJournalWriteException();
            }

            final byte isClosedGracefully = header.get();

            if (B_FALSE == isClosedGracefully) {
                throw new NotClosedGracefullyException();
            }

            header.position(19);

            sequenceCounter.set(header.getLong()); // Sequence counter
            positionIndicator.set(header.getLong()); // File end position
        }
    }

    static void markRecordProcessed(
        final long recordStartPosition,
        final boolean state,
        final boolean sync,
        final Path path,
        final long newProcessed
    ) throws IOException {
        final FileChannel writeChannel = getFileChannel(sync, path);
        final FileLock fileLock = writeChannel.tryLock(recordStartPosition, RECORD_HEADER_SIZE, true);

        if (null == fileLock) {
            throw new IOException();
        }

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(9);
        byteBuffer.put(state ? B_TRUE : B_FALSE);
        byteBuffer.putLong(state ? newProcessed : 0L);
        byteBuffer.flip();

        writeChannel.position(recordStartPosition + 22);
        writeChannel.write(byteBuffer);
        writeChannel.close();
    }

    private static FileChannel getFileChannel(final boolean sync, final Path path) throws IOException {
        final FileChannel writeChannel;
        if (sync) {
            writeChannel = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.DSYNC
            );
        } else {
            writeChannel = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            );
        }
        return writeChannel;
    }

    public void write(final byte[] bytes) throws IOException {
        write(bytes, false);
    }

    public void write(final byte[] bytes, final boolean sync) throws IOException {
        final int recordSize = RECORD_CHECKSUM_SIZE + RECORD_HEADER_SIZE + bytes.length;
        final long[] page = allocatePage(recordSize);
        final long startPosition = page[1] - recordSize;

        final FileChannel writeChannel = getFileChannel(sync, path);
        final FileLock fileLock = writeChannel.tryLock(startPosition, recordSize, true);

        if (null == fileLock) {
            throw new IOException();
        }

        writeChannel.position(startPosition);

        try {
            final CRC32 crc32 = new CRC32();
            crc32.update(bytes);

            final ByteBuffer record = ByteBuffer.allocateDirect(recordSize);
            record.put(RECORD_MAGIC_BYTE); // magic byte
            record.put(B_FALSE); // record integrity marker
            record.putInt(bytes.length); // payload size
            record.putLong(page[0]); // sequence
            record.putLong(page[2]); // timestamp
            record.put(B_FALSE); // processed flag
            record.putLong(0); // processed timestamp
            record.position(RECORD_HEADER_SIZE);
            record.put(bytes); // data
            record.putLong(crc32.getValue()); // crc

            record.flip();
            final int bytesWritten = writeChannel.write(record);

            if (bytesWritten != recordSize) {
                throw new IOException("Record size does not match the number of bytes written to disk");
            }

            writeChannel.position(startPosition + 1);
            writeChannel.write(ByteBuffer.wrap(RECORD_WRITE_CONFIRM_FLAG));

        } catch (final IOException e) {
            archiveAndClose();
            throw e;
        } finally {
            writeChannel.close();
        }
    }

    private long[] allocatePage(final int length) throws IOException {
        if (!headerChannel.isOpen()) {
            throw new IOException();
        }

        allocationLock.lock();

        try {
            final long[] page = new long[3];

            page[0] = sequenceCounter.getAndIncrement();
            page[1] = positionIndicator.addAndGet(length);
            page[2] = System.currentTimeMillis();

            header.putLong(19, page[0]);
            header.putLong(27, page[1]);

            return page;
        } finally {
            allocationLock.unlock();
        }
    }

    public void archiveAndClose() throws IOException {
        allocationLock.lock();

        try {
            header.put(1, B_TRUE);
            header.putLong(11, System.currentTimeMillis());
            header.force();
            headerChannel.close();
        } finally {
            allocationLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        allocationLock.lock();

        try {
            header.put(2, B_TRUE);
            header.force();
            headerChannel.close();
        } finally {
            allocationLock.unlock();
        }
    }

    public void flush() {
        allocationLock.lock();

        try {
            header.force();
        } finally {
            allocationLock.unlock();
        }
    }

    public long size() throws IOException {
        return headerChannel.size();
    }

    public long sequence() {
        return sequenceCounter.get();
    }
}
