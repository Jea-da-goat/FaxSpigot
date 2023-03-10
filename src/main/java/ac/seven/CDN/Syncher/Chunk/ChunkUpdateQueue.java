package ac.seven.CDN.Syncher.Chunk;

import ac.seven.CDN.Lock.LockUtils;
import ac.seven.CDN.Syncher.IO.NetworkService;
import ac.seven.CDN.Syncher.IO.NetworkUtils;
import ac.seven.CDN.Syncher.Utils.Coords;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;

public class ChunkUpdateQueue {
    private LockUtils<String> ServerLock = new LockUtils<>();

    private LockUtils<String> getServerLock() {
        return ServerLock;
    }
    private HashMap<String, String> blockUpdates = new HashMap<>();
    private HashMap<String, HashMap<String, String>> chunkUpdates = new HashMap<>();

    private String ServerName;

    public ChunkUpdateQueue(String ServerName, Long updateDelay) {
        this.ServerName = ServerName;
        new Thread(() -> {
            while(NetworkUtils.isExistingServer(ServerName)) {
                try {
                    Thread.sleep(updateDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.getServerLock().tryOptainLock("block");
                HashMap<String, String> blocklist = new HashMap<>(blockUpdates);
                blockUpdates.clear();
                this.getServerLock().releaseLock("block");
                this.getServerLock().tryOptainLock("chunk");
                HashMap<String, HashMap<String, String>> chunklist = new HashMap<>(chunkUpdates);
                chunkUpdates.clear();
                this.getServerLock().releaseLock("chunk");
                HashMap<String, Object> packet = new HashMap<>();
                packet.put("blockUpdate", blocklist);
                packet.put("chunkUpdate", chunklist);
                NetworkService.send(this.ServerName, packet);
            }
        }).start();

    }

    public void addBlockUpdate(String coords, String blockData) {
        this.getServerLock().tryOptainLock("block");
        blockUpdates.put(coords, blockData);
        this.getServerLock().releaseLock("block");
    }

    public void addChunkUpdate(String Chunkkey, HashMap<String, String> chunkSnapshot) {
        this.getServerLock().tryOptainLock("chunk");
        chunkUpdates.put(Chunkkey, chunkSnapshot);
        this.getServerLock().releaseLock("chunk");
    }
}
