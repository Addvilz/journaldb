package com.picoff.journaldb;

import java.io.File;

public class BaseTest {

    public File testFile(final String name) {
        final ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(name).getFile());
    }
}
