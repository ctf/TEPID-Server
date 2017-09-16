package in.waffl.q;

public class Q<T> {
	public final Promise<T> promise;
	private Q() {
		promise = new Promise<>();
	}
	public void resolve(T result) {
		promise.resolve(result);
	}
	public void reject(String reason) {
		promise.reject(reason);
	}
	public void reject(String reason, Throwable cause) {
		promise.reject(reason, cause);
	}
	public boolean resolved() {
		return promise.isResolved();
	}
	public static <T> Q<T> defer() {
		return new Q<T>();
	}

}
