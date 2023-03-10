package ac.seven.CDN.Syncher.Utils;

import ac.seven.CDN.Lock.Settings;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;

public class ChunkUtils {


    public static String getChunkkey(LevelChunk chunk) {
        return chunk.bukkitChunk.getWorld().getName() + "=" + chunk.bukkitChunk.getChunkKey();
    }

    public static String getChunkkey(Chunk chunk) {
        return chunk.getWorld().getName() + "=" + chunk.getChunkKey();
    }

    public static String[] splitKey(String Chunkkey) {
        return Chunkkey.split("=");
    }

    public static HashMap<String, String> serializeChunk(ChunkSnapshot chunkSnapshot) {
        HashMap<String, String> map = new HashMap<>();
        for(int x = 0; x <= 15; x++) {
            for(int y = Settings.minY; y <= Settings.maxY; y++) {
                for(int z = 0; z <= 15; z++) {
                    map.put(x + "=" + y + "=" + z, chunkSnapshot.getBlockData(x, y, z).getAsString());
                }
            }
        }
        return map;
    }

    public static Coords toCoords(String coorddata) {
        String[] p = coorddata.split("=");
        return new Coords(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }
    public static BlockData getBlockData(String blockdata) {
        return Bukkit.createBlockData(blockdata);
    }

}
