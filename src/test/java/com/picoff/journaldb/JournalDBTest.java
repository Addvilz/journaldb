package com.picoff.journaldb;

import com.picoff.commons.unit.DigitalUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class JournalDBTest {
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
    public void it_writes_database() throws IOException {
        final JournalDBOptions journalDBOptions = new JournalDBOptions();
        journalDBOptions.setDataDirectory(testFolder.newFolder());
        journalDBOptions.setJournalMaxSize(100, DigitalUnit.MEGABYTE);
        final JournalDB journalDB = new JournalDB(journalDBOptions);

        final byte[] fk_payload = new byte[4096];
        for (int i = 0; i < 100000; i++) {
            journalDB.write(fk_payload);
        }

        final long sequence = journalDB.getSequence();

        assertThat(sequence).isEqualTo(5);

        journalDB.flush();
        journalDB.close();
    }
}
