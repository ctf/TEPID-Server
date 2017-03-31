package ca.mcgill.science.tepid.server.util;

public class StreamDuplicationException extends RuntimeException {
	private static final long serialVersionUID = -51340336853842801L;

	public StreamDuplicationException() {
	}

	public StreamDuplicationException(String msg) {
		super(msg);
	}

	public StreamDuplicationException(Throwable parent) {
		super(parent);
	}

	public StreamDuplicationException(String msg, Throwable parent) {
		super(msg, parent);
	}


}
