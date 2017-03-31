package ca.mcgill.science.tepid.server.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

public class DuplicatedOutputStream implements Closeable, AutoCloseable {
	protected DuplicatedOutputStream() {
	}
	private final DuplicatedInputStream is = new DuplicatedInputStream();
	
	public void write(byte[] b) {
		this.write(b, 0, b.length);
	}
	public void write(byte[] buf, int offset, int length) {
		is.offerChunk(Arrays.copyOfRange(buf, Math.min(offset, buf.length - 1), Math.min(buf.length, offset + length)));
	}
	public DuplicatedInputStream getInputStream() {
		return is;
	}
	@Override
	public void close() throws IOException {
		is.close();
	}
}