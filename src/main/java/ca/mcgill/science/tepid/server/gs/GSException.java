package ca.mcgill.science.tepid.server.gs;

public class GSException extends RuntimeException {

	private static final long serialVersionUID = -2212386613903764979L;

	public GSException() {
	}

	public GSException(String msg) {
		super(msg);
	}

	public GSException(Throwable parent) {
		super(parent);
	}

	public GSException(String msg, Throwable parent) {
		super(msg, parent);
	}


}
