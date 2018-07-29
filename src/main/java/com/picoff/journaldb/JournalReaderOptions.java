package com.picoff.journaldb;

public class JournalReaderOptions {
    private boolean failOnMagicByte = true;
    private boolean failOnNotArchived = true;
    private boolean failOnNotClosedGracefully = true;

    public boolean failOnMagicByte() {
        return failOnMagicByte;
    }

    public JournalReaderOptions setFailOnMagicByte(final boolean failOnMagicByte) {
        this.failOnMagicByte = failOnMagicByte;
        return this;
    }

    public boolean failOnNotArchived() {
        return failOnNotArchived;
    }

    public JournalReaderOptions setFailOnNotArchived(final boolean failOnNotArchived) {
        this.failOnNotArchived = failOnNotArchived;
        return this;
    }

    public boolean failOnNotClosedGracefully() {
        return failOnNotClosedGracefully;
    }

    public JournalReaderOptions setFailOnNotClosedGracefully(final boolean failOnNotClosedGracefully) {
        this.failOnNotClosedGracefully = failOnNotClosedGracefully;
        return this;
    }
}
