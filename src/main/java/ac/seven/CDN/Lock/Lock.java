package ac.seven.CDN.Lock;

public class Lock<T> {

    private final T lock;

    public Lock(T lock) {
        this.lock = lock;
    }

    public T getLock() {
        return this.lock;
    }

    private boolean status = false;

    public boolean isLocked() {
        return this.status;
    }

    public void lock() {
        status = true;
    }

    public void unlock() {
        status = false;
    }

}
