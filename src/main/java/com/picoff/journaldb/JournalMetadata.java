package com.picoff.journaldb;

public class JournalMetadata {
    private final boolean isArchived;
    private final boolean isClosedGracefully;
    private final long createdAt;
    private final long archivedAt;
    private final long sequence;
    private final long position;

    JournalMetadata(
        final boolean isArchived,
        final boolean isClosedGracefully,
        final long createdAt,
        final long archivedAt,
        final long sequence,
        final long position
    ) {
        this.isArchived = isArchived;
        this.isClosedGracefully = isClosedGracefully;
        this.createdAt = createdAt;
        this.archivedAt = archivedAt;
        this.sequence = sequence;
        this.position = position;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public boolean isClosedGracefully() {
        return isClosedGracefully;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getArchivedAt() {
        return archivedAt;
    }

    public long getSequence() {
        return sequence;
    }

    public long getPosition() {
        return position;
    }
}
