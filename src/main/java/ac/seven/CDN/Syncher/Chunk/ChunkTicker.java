package ac.seven.CDN.Syncher.Chunk;

import ac.seven.CDN.Syncher.Utils.ChunkUtils;
import ac.seven.CDN.Syncher.Utils.Coords;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.HashMap;

public class ChunkTicker {

    public static void loadChunk(String Chunkkey, HashMap<String, String> chunkdata) {
        String[] p = ChunkUtils.splitKey(Chunkkey);
        String worldname = p[0];
        Long longkey = Long.valueOf(p[1]);
        Chunk chunk = Bukkit.getWorld(worldname).getChunkAt(longkey);
        chunkdata.entrySet().forEach(e -> {
            Coords coords = ChunkUtils.toCoords(e.getKey());
            chunk.getBlock(coords.getX(), coords.getY(), coords.getZ()).setBlockData(ChunkUtils.getBlockData(e.getValue()));
        });
        chunk.setForceLoaded(true);
    }

    public static void localloader(Chunk chunk) {

    }

    public static void unloadChunk(String Chunkkey) {
        String[] p = ChunkUtils.splitKey(Chunkkey);
        String worldname = p[0];
        Long longkey = Long.valueOf(p[1]);
        Chunk chunk = Bukkit.getWorld(worldname).getChunkAt(longkey);
        chunk.unload(true);
    }
}
