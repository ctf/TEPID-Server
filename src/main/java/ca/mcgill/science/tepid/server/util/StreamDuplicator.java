package ca.mcgill.science.tepid.server.util;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StreamDuplicator extends Thread {
    private final Queue<DuplicatedInputStream> streams = new ConcurrentLinkedQueue<>();
    private InputStream is;
    private final int chunkSize;

    public static DuplicatedOutputStream makePipe() {
        return new DuplicatedOutputStream();
    }

    public StreamDuplicator(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public InputStream getInputStream() {
        DuplicatedInputStream dis;
        try {
            dis = new DuplicatedInputStream();
        } catch (Exception e) {
            throw new StreamDuplicationException(e);
        }
        streams.add(dis);
        return dis;
    }

    public StreamDuplicator setInputStream(InputStream is) {
        this.is = is;
        return this;
    }

    @Override
    public void run() {
        if (this.is == null) throw new StreamDuplicationException("Source InputStream not set");
        try {
            int bytesRead;
            byte[] buf = new byte[chunkSize];
            System.out.println("writing chunks");
            while ((bytesRead = is.read(buf)) > 0) {
                byte[] chunk = Arrays.copyOfRange(buf, 0, bytesRead);
                for (DuplicatedInputStream dis : streams) {
                    dis.offerChunk(chunk);
                }
            }
            for (DuplicatedInputStream dis : streams) {
                dis.close();
            }
        } catch (Exception e) {
            throw new StreamDuplicationException(e);
        }
    }
}
