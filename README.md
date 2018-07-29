# JournalDB
## Low level embedded journaling database for JVM 1.8+

### About

JournalDB is a low level append-only journaling database, useful for storing and retrieving binary operations logs,
store failure recovery records, oplogs, or any free form binary data really. It was designed to be the default storage backend for Geoste Server
fail-to-write event logs, however it can be used to persist pretty much any kind of binary data.

### Installation

JournalDB is available in Maven Central.

```xml
<dependency>
    <groupId>com.picoff</groupId>
    <artifactId>journaldb</artifactId>
    <version>VERSION</version>
</dependency>
```

### Usage example

```java
import com.picoff.commons.unit.DigitalUnit;
import com.picoff.journaldb.*;

import java.io.File;
import java.io.IOException;

class Main {
    public static void main(final String[] args) throws IOException {
        final JournalDBOptions options = new JournalDBOptions();

        options.setDataDirectory(new File("./database/"));
        options.setJournalMaxSize(5, DigitalUnit.GIGABYTE); // Automatically relocate if the current journal reaches this size (approximately)
        options.setRelocateOnBootFailure(false); // Relocate if the current active journal was not closed properly
        options.setRelocateOnWriteFailure(false);  // Relocate on failing to write a journal

        final JournalDB journalDB = new JournalDB(options);

        journalDB.write("some data".getBytes()); // Without sync to hardware
        journalDB.write("some sync data".getBytes(), true); // With sync to hardware

        // ........

        // Relocate and archive the current journal
        final long previousSequence = journalDB.relocate();

        final JournalReaderOptions readerOptions = new JournalReaderOptions();
        readerOptions.setFailOnMagicByte(true); // Fail if journal file does not begin with the magic byte
        readerOptions.setFailOnNotArchived(true); // Fail if attempting to read a non-archived journal
        readerOptions.setFailOnNotClosedGracefully(true); // Fail if journal was not closed gracefully

        final JournalReader reader = journalDB.createReader(readerOptions, previousSequence);

        final EntryReadOptions readOptions = new EntryReadOptions();
        readOptions.setVerifyChecksum(true); // Fail if data read from the disk does not match the stored checksum
        readOptions.setFailOnIntegrityByte(true); // Fail if record does not have integrity byte set to 1
        readOptions.setFailOnMagicByte(true); // Fail if entry does not start with the magic byte

        readOptions.setReadFilter(meta -> {
            // Filter record by its metadata, without reading the actual data.
            return true;
        });

        readOptions.setStartPosition(Journal.FILE_HEADER_SIZE); // Set the position where to start reading from, normally - from the end of the header.

        reader.forEachEntry(readOptions, entry -> {
            final byte[] data = entry.getData();

            try {
                entry.writeProcessedState(true, true); // Optionally, mark record as processed in sync mode.
            } catch (final IOException e) {
                e.printStackTrace();
            }
        });

        journalDB.flush();
        journalDB.close();
    }
}
```

### Failure and recovery

JournalDB has some built in integrity checks (like double-write of the integrity bit for journal entries, etc),
however as with any database, this is done on best effort basis. Random hardware failure could potentially corrupt
some data, but given the simple binary format of the journal files and extensive options available for the built in reader,
recovery in case of hard failure should be trivial. 

Entries of special significance can also be written in "sync" mode,
meaning the method invocation will only return when all the data has been written and flushed to the underlying hardware
device. Sync mode has significant performance penalty.

Whenever journal fails to be written, it will automatically close itself and prevent any other writes to that journal file.
It will also emit an IOException and, depending on the options, either auto-close the database preventing any further writes,
or attempt to relocate to a new journal on best effort basis. If that is not possible, JournalDB will attempt to close
itself, and all future write will fail.

In either case, JournalDB suffers from all the same issues that can be attributed to any single node, single point of failure setup. 
For mission critical data, we suggest JournalDB be only used as an additional layer of persistence, in addition to replicated
and distributed systems, for example, Apache Kafka or Apache BookKeeper.

### Journal states

Journals can be either "active" or "archived". Active journals are ones currently being written, archived journals
are journals those where all write operations are complete and they are safe to read.

You should not read non-archived journals (ones that have not been relocated out of the current JournalDB instance).
You can force reading of such file via options, but it can lead to unpredicted results.

### Entry order

Entries are guaranteed to be written in the order of allocation, even in parallel. This is because each
Journal contains internal sequence number, and sequence numbers are in fact allocated synchronously, one by one.
Therefore entry order with one journal file is guaranteed to be absolute.

### Reading entries and marking entries as processed

JournalDB allows to mark journal entries as "processed" whenever you are iterating and reading journal files.
This can be conceptually used as a way to mark some entries recovered or can mean literally anything - usage of this
flag is to be defined by the user.

Marking entry processed write a magic byte and timestamp to the journal file (even archived one!), and can be retrieved
when iterating later.

This feature is useful, for example, to mark records "processed" or "recovered" in some scope.

### Journal file format

JournalDB stores data in files called journals. Journal is a binary file and follows this format:

#### Header

Header contains journal file metadata and is 100 bytes long.

| Offset | Length | Description                                                                                                  |
| ------ | ------ | ------------------------------------------------------------------------------------------------------------ |
| 0      | 1      | Magic byte, character 'j'                                                                                    |
| 1      | 1      | Archive marker, 0 or 1. Indicates whether or not this journal is "archived" and can not be written any more. |
| 2      | 1      | Graceful close marker, 0 or 1. Indicates whether this file has been closed gracefully.                       |
| 3      | 8      | UNIX timestamp on when this journal was created, milliseconds since epoch                                    |
| 11     | 8      | UNIX timestamp on when this journal was archived, milliseconds since epoch                                   |
| 19     | 8      | Next available entry sequence number                                                                         |
| 27     | 8      | Last write position known, offset bytes from start of file                                                   |
| 35     | 65     | Reserved for future use                                                                                      |
 
#### Entry region

Bytes after header region is so called entry-space. This region contains variable length entries and metadata written to the journal.

Each entry contains 48 bytes of metadata, variable length data region and 8 bytes long CRC checksum.

The maximum length of the data stored per record is limited to the maximum size of `byte[]` in JVM minus some 100 bytes,
depending on the JVM implementation used.


| Offset | Length | Description                                                                                           |
| ------ | ------ | ----------------------------------------------------------------------------------------------------- |
| 0      | 1      | Magic byte, 'r'                                                                                       |
| 1      | 1      | Record integrity marker, 0 or 1. Normally always 1, unless the record got corrupted on write somehow. |
| 2      | 4      | Record length in bytes (integer)                                                                      |
| 6      | 8      | Record sequence number                                                                                |
| 14     | 8      | UNIX timestamp on when this record was created, milliseconds since epoch                              |
| 22     | 1      | Entry processing flag (see bellow)                                                                    |
| 23     | 8      | Entry processing timestamp (see bellow)                                                               |
| 31     | 17     | Reserved for future use                                                                               |
| 48     | ???    | Data                                                                                                  |
| ???    | 8      | CRC32 checksum of the data in the record                                                              |


## State of the library

JournalDB is considered to be ready for use in production, however, this is a relatively new library 
and should be treated as such. Some things might still need some ironing out.

## License
Apache License, version 2.0
