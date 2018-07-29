package com.picoff.journaldb;

import com.google.common.collect.Range;
import com.picoff.journaldb.exception.ArchivedJournalWriteException;
import com.picoff.journaldb.exception.JournalMagicByteException;
import com.picoff.journaldb.exception.NotClosedGracefullyException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;

public class JournalTest extends BaseTest {
    private static final byte[] PAYLOAD = "payload".getBytes();

    private final TemporaryFolder testFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        testFolder.create();
    }

    @After
    public void tearDown() {
        testFolder.delete();
    }

    @Test
    public void it_writes_journal() throws IOException {
        final File journalFile = writeSingleEntryJournal();

        final JournalReader journalReader = new JournalReader(journalFile);

        final List<JournalEntry> entries = new ArrayList<>();
        journalReader.forEachEntry(entries::add);

        assertThat(entries).hasSize(1);

        final JournalEntry entry = entries.get(0);

        assertThat(entry).isNotNull();

        assertThat(entry.getChecksum()).isEqualTo(1110206997);
        assertThat(entry.getDataSize()).isEqualTo(7);
        assertThat(entry.getProcessed()).isEqualTo(false);
        assertThat(entry.getProcessedTimestamp()).isEqualTo(0);
        assertThat(entry.getSequence()).isEqualTo(0);
        assertThat(entry.getTimestamp()).isIn(Range.closed(
            System.currentTimeMillis() - 5000,
            System.currentTimeMillis()
        ));
        assertThat(entry.getData()).isEqualTo(PAYLOAD);
    }

    private File writeSingleEntryJournal() throws IOException {
        final File journalFile = testFolder.newFile();
        final Journal journal = new Journal(journalFile);

        journal.write(PAYLOAD);
        journal.flush();

        assertThat(journal.size()).isEqualTo(163);

        journal.close();


        return journalFile;
    }

    @Test(expected = JournalMagicByteException.class)
    public void it_fails_to_open_a_file_that_is_not_a_journal() throws IOException {
        new Journal(testFile("not_journal_file.jdf"));
    }

    @Test(expected = ArchivedJournalWriteException.class)
    public void it_fails_to_open_a_file_that_is_archived() throws IOException {
        new Journal(testFile("archived_journal.jdf"));
    }

    @Test(expected = NotClosedGracefullyException.class)
    public void it_fails_to_open_a_file_that_is_not_closed_gracefully() throws IOException {
        new Journal(testFile("non_graceful_close.jdf"));
    }

    @Test
    public void it_does_not_explode_in_fire_while_writing_concurrently() throws IOException, InterruptedException {
        final File journalFile = testFolder.newFile();
        final Journal journal = new Journal(journalFile);

        final ExecutorService executor = Executors.newFixedThreadPool(10);

        final AtomicInteger seq = new AtomicInteger(-1);
        final Runnable writer = () -> {
            final int jobSeq = seq.incrementAndGet();
            final ByteBuffer payload = ByteBuffer.allocate(8);
            payload.putInt(0, jobSeq);

            for (int i = 0; i < 10000; i++) {
                payload.putInt(4, i);

                try {
                    journal.write(payload.array());
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        };

        executor.execute(writer);
        executor.execute(writer);
        executor.execute(writer);
        executor.execute(writer);
        executor.execute(writer);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        assertThat(journal.sequence()).isEqualTo(50000);
        assertThat(journal.size()).isEqualTo(3200100);

        journal.close();

        final JournalReader journalReader = new JournalReader(journalFile);

        journalReader.forEachEntry(entry -> {
            final byte[] data = entry.getData();
            final ByteBuffer wrap = ByteBuffer.wrap(data);

            assertThat(wrap.getInt(0)).isIn(Range.closed(0, 4));
            assertThat(wrap.getInt(1)).isIn(Range.closed(0, 9999));
        });

        journalReader.close();
    }
}
