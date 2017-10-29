package ca.mcgill.science.tepid.server.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DuplicatedInputStream extends InputStream {
    private byte[] chunk;
    private int offset = 0;
    private final BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();

    protected DuplicatedInputStream() {
    }

    @Override
    public int read() throws IOException {
        if (chunk == null || offset >= chunk.length) readChunk();
        if (chunk.length == 0) return -1;
        return chunk[offset++];
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        if (chunk == null || offset >= chunk.length) readChunk();
        len = Math.min(len, chunk.length - offset);
        if (len > 0) {
            System.arraycopy(chunk, offset, b, off, len);
            offset += len;
            return len;
        } else {
            return -1;
        }
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.offerChunk(new byte[0]);
    }

    private void readChunk() throws IOException {
        try {
            this.chunk = chunks.take();
            offset = 0;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted during chunk read");
        }
    }

    protected synchronized void offerChunk(byte[] b) {
        this.chunks.offer(b);
    }

}
