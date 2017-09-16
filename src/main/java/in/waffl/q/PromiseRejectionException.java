package in.waffl.q;

public class PromiseRejectionException extends RuntimeException {
	private static final long serialVersionUID = 8849404795231493548L;

	public PromiseRejectionException() {
	}

	public PromiseRejectionException(String msg) {
		super(msg);
	}

	public PromiseRejectionException(Throwable parent) {
		super(parent);
	}

	public PromiseRejectionException(String msg, Throwable parent) {
		super(msg, parent);
	}


}
