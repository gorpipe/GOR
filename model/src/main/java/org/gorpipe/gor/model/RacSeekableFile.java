/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package org.gorpipe.gor.model;

import org.gorpipe.gor.driver.adapters.StreamSourceSeekableFile;
import org.gorpipe.gor.driver.providers.stream.sources.file.FileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;

/**
 * RacSeekableFile implements the SeekableFile contract on top of javas RandomAccessFile
 *
 * @version $Id$
 */
public class RacSeekableFile extends StreamSourceSeekableFile {
    private static final Logger log = LoggerFactory.getLogger(RacSeekableFile.class);
    private final RandomAccessFile raf;
    private static boolean doTrace = false;
    private boolean open = true;
    private Exception traceException;

    /**
     * Construct a RacSeekableFile for the specifed RandomAccessFile file
     *
     * @param raf      The random access file
     * @param fileName The file object of the random access file
     */
    public RacSeekableFile(RandomAccessFile raf, String fileName) {
        super(new FileSource(fileName));
        this.raf = raf;
        if (doTrace) {
            traceException = new RuntimeException();
        }
    }

    @Override
    public long getFilePointer() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public long length() throws IOException {
        return raf.length();
    }

    @Override
    public void seek(long pos) throws IOException {
        raf.seek(pos);
    }

    @Override
    public void close() throws IOException {
        raf.close();
        open = false;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return raf.read(b, off, len);
    }

    @Override
    protected void finalize() throws Throwable {
        if (open) {
            doTrace = true;  // Turn exception trace on
            String msg = this.getClass().getName() + " - not closed on finalize.";
            if (traceException != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pr = new PrintWriter(sw);
                traceException.printStackTrace(pr);
                pr.flush();
                msg = msg + "\n Open Trace:\n" + sw;
            }
            log.info(msg);
            close();
        }
        super.finalize();
    }
}
