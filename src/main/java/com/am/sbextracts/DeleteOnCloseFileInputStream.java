package com.am.sbextracts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteOnCloseFileInputStream extends FileInputStream {

    private final Logger LOGGER = LoggerFactory.getLogger(DeleteOnCloseFileInputStream.class);

    private File file;

    public DeleteOnCloseFileInputStream(String fileName) throws FileNotFoundException {
        this(new File(fileName));
    }

    public DeleteOnCloseFileInputStream(File file) throws FileNotFoundException {
        super(file);
        this.file = file;
    }

    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (file != null) {
                LOGGER.info("file {} deleted : {}", file.getName(), file.delete());
                file = null;
            }
        }
    }
}
