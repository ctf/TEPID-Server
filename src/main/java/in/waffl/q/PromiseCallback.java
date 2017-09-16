package in.waffl.q;

public abstract class PromiseCallback<T> {
	public abstract void resolved(T result);
	public void rejected(String reason, Throwable cause) {}
}
