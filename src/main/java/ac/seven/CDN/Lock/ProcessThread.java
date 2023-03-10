package ac.seven.CDN.Lock;

import java.util.ArrayList;
import java.util.List;

public class ProcessThread {


    private final List<Runnable> runnables = new ArrayList<>();

    private boolean shouldrun = true;
    public ProcessThread() {
        new Thread(() -> {
            while(shouldrun) {
                while (runnables.isEmpty()) {
                    Thread.onSpinWait();
                }
                runnables.forEach(Runnable::run);
            }
        }).start();
    }

    public void stop() {
        shouldrun = false;
    }



    public void run(Runnable runnable) {
        runnables.add(runnable);
    }
}
