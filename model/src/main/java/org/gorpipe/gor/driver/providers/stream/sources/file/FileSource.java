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

package org.gorpipe.gor.driver.providers.stream.sources.file;

import com.sun.istack.NotNull;
import org.apache.commons.io.FileUtils;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.meta.*;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSourceMetadata;
import org.gorpipe.gor.table.util.PathUtils;
import org.gorpipe.util.WrappingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.stream.Stream;

/**
 * Represents a data source accessed through file system.
 * <p>
 * Created by villi on 22/08/15.
 */
public class FileSource implements StreamSource {

    private static final Logger log = LoggerFactory.getLogger(FileSource.class);

    private final SourceReference sourceReference;
    private final Path filePath;
    private RandomAccessFile raf;
    private final boolean useAtomicWrite = System.getProperty("gor.filereader.atomicwrite", "true").equalsIgnoreCase("true");;
    private Path atomicTempFilePath;  // Set when we should write to a temp file.

    // For debugging purposes, set the system property 'gor.filereader.stacktrace' to true
    // to keep track of the call stack when the 'raf' is opened. The call stack is then
    // logged out if the file is closed during finalize.
    private final boolean keepStackTraceForOpen = System.getProperty("gor.filereader.stacktrace", "false").equalsIgnoreCase("true");
    private Error rafOpenedStackTrace;

    /**
     * Name of file.  This should be the full path to the file.
     */
    public FileSource(String fileName) {
        this(new SourceReferenceBuilder(fileName).build());
    }

    /**
     * Name of file.  This should be the full path to the file.
     */
    public FileSource(SourceReference sourceReference) {
        this.sourceReference = fixSourceReference(sourceReference);
        this.filePath = getFullPath(this.sourceReference);
    }

    private SourceReference fixSourceReference(SourceReference sourceReference) {
        String fileName = sourceReference.getUrl();
        fileName = PathUtils.fixFileSchema(fileName);
        if (!fileName.equals(sourceReference.getUrl())) {
            sourceReference = new SourceReference(fileName, sourceReference);
        }
        return sourceReference;
    }

    private Path getFullPath(SourceReference sourceReference) {
        String fileName = sourceReference.getUrl();
        if (sourceReference.getCommonRoot() != null && !Paths.get(fileName).isAbsolute()) {
            fileName = Paths.get(sourceReference.getCommonRoot(), fileName).toString();
        }
        return Paths.get(fileName);
    }

    @Override
    public InputStream open(long start)  {
        ensureOpenForRead();
        try {
            raf.seek(start);
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath().toString()).retry();
        }
        return new FileSourceStream();
    }

    @Override
    public InputStream openClosable() {
        ensureOpenForRead();
        return new FileSourceStream(true);
    }

    @Override
    public OutputStream getOutputStream(long start)  {
        ensureOpenForWrite(start > 0);
        try {
            raf.seek(start);
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath().toString()).retry();
        }
        return new FileSourceOutputStream();
    }

    private void ensureOpenForRead()  {
        if (raf == null) {
            if (keepStackTraceForOpen) {
                rafOpenedStackTrace = new Error();
            }

            try {
                raf = new RandomAccessFile(filePath.toFile(), "r");
            } catch (FileNotFoundException e) {
                throw new GorResourceException("Input source does not exist:" + getPath().toString(), getPath().toString(), e);
            }
        }
    }

    private void ensureOpenForWrite(boolean hasSeek)  {
        if (raf == null) {
            if (keepStackTraceForOpen) {
                rafOpenedStackTrace = new Error();
            }
            try {
                raf = new RandomAccessFile(findOutputFilePath(hasSeek).toFile(), "rw");
            } catch (FileNotFoundException e) {
                throw new GorResourceException("Input source does not exist:" + getPath().toString(), getPath().toString(), e);
            }
        }
    }

    private Path findOutputFilePath(boolean hasSeek) {
        // Only use temp files atomic write is on and we are not seeking in the file, i.e. not overwriting the whole file.
        if (useAtomicWrite && !hasSeek) {
            atomicTempFilePath = PathUtils.getTempFilePath(filePath);
        } else {
            atomicTempFilePath = null;
        }
        return atomicTempFilePath != null ? atomicTempFilePath : filePath;
    }

    @Override
    public InputStream open(long start, long minLength)  {
        return open(start);
    }

    @Override
    public InputStream open()  {
        return open(0);
    }
    
    @Override
    public String getFullPath() {
        return filePath.toString();
    }

    @Override
    public Path getPath() {
        return filePath;
    }

    @Override
    public OutputStream getOutputStream(boolean append)  {
        OutputStream os;
        try {
            var parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (append) {
                os = Files.newOutputStream(filePath, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } else {
                os = Files.newOutputStream(findOutputFilePath(false), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
        return new WrappingOutputStream(os) {
            @Override
            public void close() throws IOException  {
                try {
                    super.close();
                } finally {
                    FileSource.this.close();
                }
            }
        };
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public SourceType getSourceType() {
        return FileSourceType.FILE;
    }

    @Override
    public DataType getDataType() {
        return DataType.fromFileName(sourceReference.getUrl());
    }

    @Override
    public boolean exists() {
        return Files.exists(filePath);
    }

    @Override
    public String move(DataSource dest)  {
        try {
            return Files.move(Path.of(getFullPath()), Path.of(dest.getFullPath()),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE).toString();
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public String copy(DataSource dest)  {
        try {
            return Files.copy(Path.of(getFullPath()), Path.of(dest.getFullPath())).toString();
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public String createDirectory(FileAttribute<?>... attrs)  {
        try {
            return Files.createDirectory(filePath, attrs).toString();
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public String createDirectories(FileAttribute<?>... attrs)  {
        try {
            return Files.createDirectories(filePath, attrs).toString();
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(filePath);
    }

    @Override
    public void delete()  {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public void deleteDirectory()  {
        try {
            FileUtils.deleteDirectory(filePath.toFile());
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public Stream<String> list()  {
        try {
            return mapPathToRelative(Files.list(filePath));
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    @Override
    public Stream<String> walk()  {
        try {
            return mapPathToRelative(Files.walk(filePath));
        } catch (IOException e) {
            throw GorResourceException.fromIOException(e, getPath()).retry();
        }
    }

    private Stream<String> mapPathToRelative(Stream<Path> pathStream) {
            if (!PathUtils.isAbsolutePath(sourceReference.getUrl())) {
                var commonRoot = sourceReference.getCommonRoot();
                if (commonRoot != null) {
                    var root = Path.of(sourceReference.getCommonRoot());
                    return pathStream.map(root::relativize).map(Path::toString);
                }
            }
                return pathStream.map(Path::toString);
    }

    @Override
    public StreamSourceMetadata getSourceMetadata()  {
        // TODO:  Why does FileSource behave differently than most other sources.  We should only af the exists case here.
        var exists = Files.exists(filePath);
        if (exists) {
            try {
                return new StreamSourceMetadata(this, "file://" + filePath.toRealPath(), Files.getLastModifiedTime(filePath).toMillis(), Files.size(filePath), null);
            } catch (IOException e) {
                throw GorResourceException.fromIOException(e, getPath()).retry();
            }
        } else {
            return new StreamSourceMetadata(this, "file://" + filePath.normalize().toAbsolutePath(), 0L, 0L, null);
        }
    }

    @Override
    public SourceReference getSourceReference() {
        return sourceReference;
    }

    @Override
    public void close()  {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                throw GorResourceException.fromIOException(e, getPath()).retry();
            } finally {
                raf = null;
            }
        }

        if (useAtomicWrite && atomicTempFilePath != null && !filePath.equals(atomicTempFilePath)) {
            try {
                Files.move(atomicTempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                atomicTempFilePath = null;
            } catch (IOException e) {
                throw GorResourceException.fromIOException(e, getPath()).retry();
            }
        }
    }

    @Override
    protected void finalize() {
        if (raf != null) {
            String msg = "Datasource closed via finalize method: " + filePath.toAbsolutePath();
            if (rafOpenedStackTrace != null) {
                StringWriter sw = new StringWriter();
                rafOpenedStackTrace.printStackTrace(new PrintWriter(sw));
                log.warn("{}\nOpened at {}", msg, sw);
            } else {
                log.warn(msg);
            }
        }
        close();
    }

    /**
     * Implementation of an InputStream on top of a RandomAccessFile.
     * <p>
     * Closing the stream does not close the underlying RAF so it can be efficiently reused by
     * subsequent opens.
     */
    public class FileSourceStream extends InputStream {
        private long mark;
        private final boolean closable;

        public FileSourceStream() {
            this(false);
        }

        public FileSourceStream(boolean closable) {
            this.closable = closable;
        }

        @Override
        public int read() throws IOException {
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return raf.read(b, off, len);
        }

        @Override
        public void close() {
            if (closable && raf != null) {
                FileSource.this.close();
            }
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(raf.length() - raf.getFilePointer(), Integer.MAX_VALUE);
        }

        @Override
        public long skip(long n) throws IOException {
            raf.seek(raf.getFilePointer() + n);
            return n;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readLimit) {
            try {
                this.mark = raf.getFilePointer();
            } catch (IOException e) {
                throw GorResourceException.fromIOException(e, getPath());
            }
        }

        @Override
        public synchronized void reset() throws IOException {
            raf.seek(mark);
        }

        @Override
        public int read(@NotNull byte[] b) throws IOException {
            return raf.read(b);
        }

        @Override
        public String toString() {
            return raf.toString();
        }

        @Override
        public int hashCode() {
            return raf.hashCode();
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException(this.getClass().getName());
        }
    }

    /**
     * Implementation of an InputStream on top of a RandomAccessFile.
     * <p>
     * Closing the stream does not close the underlying RAF so it can be efficiently reused by
     * subsequent opens.
     */
    public class FileSourceOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            raf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            raf.write(b, off, len);
        }

        @Override
        public void close() {
            FileSource.this.close();
        }

        @Override
        public void write(byte[] b) throws IOException {
            raf.write(b);
        }

        @Override
        public String toString() {
            return raf.toString();
        }

        @Override
        public int hashCode() {
            return raf.hashCode();
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException(this.getClass().getName());
        }
    }
}
