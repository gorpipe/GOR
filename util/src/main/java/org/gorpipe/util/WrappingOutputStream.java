package org.gorpipe.util;

import java.io.IOException;
import java.io.OutputStream;

public class WrappingOutputStream extends OutputStream {
    private OutputStream outputStream;

    public WrappingOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(byte[] b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        outputStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
    }
}
