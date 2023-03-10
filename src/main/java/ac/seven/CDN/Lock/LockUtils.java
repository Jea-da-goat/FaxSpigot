package ac.seven.CDN.Lock;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

public class LockUtils<T> {

    private HashMap<T, Lock> lock_ = new HashMap<>();
    private final HashMap<T, Lock> sync_lock = new HashMap<>();

    private final Object Locallock = new Object();

    public Lock Lock(T address) {
        return lock_.get(address);
    }

    public void tryOptainLock(T address) {
        if(lock_.containsKey(address)) {
            synchronized (lock_.get(address).getLock()) {
                while (lock_.get(address).isLocked()) {
                    Thread.onSpinWait();
                }
                lock_.get(address).lock();
            }
        } else {
            synchronized (sync_lock) {
                if (!sync_lock.containsKey(address)) {
                    Lock<T> lock = new Lock<T>(address);
                    sync_lock.put(address, lock);
                    lock_.put(address, lock);
                }
                synchronized (sync_lock.get(address).getLock()) {
                    while (sync_lock.get(address).isLocked()) {
                        Thread.onSpinWait();
                    }
                    sync_lock.get(address).lock();
                }
            }
        }
    }

    public void releaseLock(T address) {
        lock_.get(address).unlock();
    }
}
