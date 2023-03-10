package ac.seven.TaskUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TickUtils {


    //private static Thread thread1;
    private static final List<Runnable> runnableList1 = new ArrayList<>();

    //private static Thread thread2;
    private static final List<Runnable> runnableList2 = new ArrayList<>();

    //private static Thread thread3;
    private static final List<Runnable> runnableList3 = new ArrayList<>();

    //private static Thread thread4;
    private static final List<Runnable> runnableList4 = new ArrayList<>();


    /*private static boolean started = false;
    public static void CreateThreadPulls() {

        if(started) {
            return;
        }
        started = true;
        thread1 = new Thread(() -> {
            while(true) {
                while(runnableList1.isEmpty()) {
                    Thread.onSpinWait();
                }
                synchronized (runnableList1) {
                    runnableList1.forEach(Runnable::run);
                    runnableList1.clear();
                }
            }
        });
        thread2 = new Thread(() -> {

        });
        thread3 = new Thread(() -> {

        });
        thread4 = new Thread(() -> {

        });
    }*/

    private static int cache = 1;
    public static void RunnableRun(Runnable runnable) {
        if(cache == 1) {
            cache = 2;
            runnableList1.add(runnable);
        } else if (cache == 2) {
            cache = 3;
            runnableList2.add(runnable);
        } else if (cache == 3) {
            cache = 4;
            runnableList3.add(runnable);
        } else if (cache == 4) {
            cache = 1;
            runnableList4.add(runnable);
        } else {
            cache = 1;
            runnableList4.add(runnable);
        }
    }

    public static List<CompletableFuture<Boolean>> startupEngine() {
        CompletableFuture<Boolean> hasFinished1 = new CompletableFuture<>();
        CompletableFuture<Boolean> hasFinished2 = new CompletableFuture<>();
        CompletableFuture<Boolean> hasFinished3 = new CompletableFuture<>();
        CompletableFuture<Boolean> hasFinished4 = new CompletableFuture<>();
        new Thread(() -> {
            try {
                runnableList1.forEach(Runnable::run);
                hasFinished1.complete(true);
            } catch (Exception e) {
                hasFinished1.complete(false);
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                runnableList2.forEach(Runnable::run);
                hasFinished2.complete(true);
            } catch (Exception e) {
                hasFinished2.complete(false);
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                runnableList3.forEach(Runnable::run);
                hasFinished3.complete(true);
            } catch (Exception e) {
                hasFinished3.complete(false);
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                runnableList4.forEach(Runnable::run);
                hasFinished4.complete(true);
            } catch (Exception e) {
                hasFinished4.complete(false);
                e.printStackTrace();
            }
        }).start();
        List<CompletableFuture<Boolean>> hasFinished = new ArrayList<>();
        hasFinished.add(hasFinished1);
        hasFinished.add(hasFinished2);
        hasFinished.add(hasFinished3);
        hasFinished.add(hasFinished4);
        return hasFinished;
    }

}
