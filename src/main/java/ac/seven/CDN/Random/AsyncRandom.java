package ac.seven.CDN.Random;

import ca.spottedleaf.concurrentutil.completable.Completable;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class AsyncRandom {

    private static HashMap<String, CompletableFuture<Integer>> map = new HashMap<>();

    public static void put(String key, CompletableFuture<Integer> completableFuture) {
        map.put(key, completableFuture);
    }

    public static boolean contains(String key) {
        return map.containsKey(key);
    }

    public static CompletableFuture<Integer> get(String key) {
        return map.get(key);
    }

    public static void createNew(String key) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        new Thread(() -> {
            Random random = new Random();
            completableFuture.complete(random.nextInt(16^3));
        }).start();
        put(key, completableFuture);
    }
}
