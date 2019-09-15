package in.waffl.q;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class Promise<T> {
    private T result;
    private Semaphore lock = new Semaphore(0);
    private String reason;
    private Throwable cause;
    private boolean resolved;
    private Queue<PromiseCallback<T>> listeners = new ConcurrentLinkedQueue<>();

    protected Promise() {
    }

    public T getResult() {
        try {
            lock.acquire();
        } catch (InterruptedException e) {
        }
        if (reason != null) {
            if (cause == null) {
                throw new PromiseRejectionException(reason);
            } else {
                throw new PromiseRejectionException(reason, cause);
            }
        }
        return result;
    }

    public T getResult(long ms) {
        try {
            if (!lock.tryAcquire(1, ms, TimeUnit.MILLISECONDS)) return null;
        } catch (InterruptedException e) {
        }
        if (reason != null) {
            if (cause == null) {
                throw new PromiseRejectionException(reason);
            } else {
                throw new PromiseRejectionException(reason, cause);
            }
        }
        return result;
    }

    protected void resolve(T result) {
        if (resolved) throw new RuntimeException("Promise already resolved");
        resolved = true;
        this.result = result;
        lock.release();
        while (!listeners.isEmpty()) {
            listeners.poll().resolved(result);
        }
    }

    protected void reject(String reason) {
        if (resolved) throw new RuntimeException("Promise already resolved");
        resolved = true;
        this.reason = reason;
        lock.release();
        while (!listeners.isEmpty()) {
            listeners.poll().rejected(reason, null);
        }
    }

    protected void reject(String reason, Throwable cause) {
        if (resolved) throw new RuntimeException("Promise already resolved");
        resolved = true;
        this.reason = reason;
        this.cause = cause;
        lock.release();
        while (!listeners.isEmpty()) {
            listeners.poll().rejected(reason, cause);
        }
    }

    protected boolean isResolved() {
        return this.resolved;
    }

    public void then(PromiseCallback<T> cb) {
        if (!this.resolved) {
            listeners.add(cb);
        } else {
            if (this.reason != null) {
                cb.rejected(reason, cause);
            } else {
                cb.resolved(result);
            }
        }
    }
}
