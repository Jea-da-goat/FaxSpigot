package ac.seven.CDN.Syncher.Chunk;

import ac.seven.CDN.Lock.LockUtils;
import ac.seven.CDN.Syncher.IO.NetworkService;
import ac.seven.CDN.Syncher.Utils.ChunkUtils;
import ac.seven.CDN.Syncher.Utils.Coords;
import io.papermc.paper.math.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSyncher {


    private static ConcurrentHashMap<String, ArrayList<String>> ChunkListener = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, ArrayList<String>> ServerListener = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<String, String> blockupdates = new ConcurrentHashMap<>();
    public static void addQueue(ChunkHolder holder) {
        LevelChunk chunk = holder.getFullChunk();

    }

    public static void ChunkSyncTask() {
        new Thread(() -> {

            while(true) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }

        }).start();
    }

    public static void broadcastChunkUpdate() {

    }

    public static void RemoteChunkImport() {

    }

    private static LockUtils<String> chunkLock = new LockUtils<>();

    public static LockUtils<String> LocalChunkLock() {
        return chunkLock;
    }

    public static void broadcastBlockUpdate(Level world, BlockPos bp, BlockState blockState) {
        /*CraftBlockData blockdata1 = blockState.createCraftBlockData();
        BlockData blockData = blockdata1.clone();
        String coords = world.getWorld().getName() + "=" + bp.getX() + "=" + bp.getY() + "=" + bp.getZ();
        Location loc = new Location(world.getWorld(), bp.getX(), bp.getY(), bp.getZ());
        //blockupdates.put(coords, blockData.getAsString());
        new Thread(() -> {
            broadcastonlyToServersListening(loc, coords, blockData);
        }).start();*/
    }

    private static void broadcastonlyToServersListening(Location loc, String coords, BlockData blockData) {
        String Chunkkey = ChunkUtils.getChunkkey(loc.getChunk());
        LocalChunkLock().tryOptainLock(Chunkkey);
        ChunkListener.get(Chunkkey).forEach(server -> {});
        LocalChunkLock().releaseLock(Chunkkey);
    }

    public static void registerBlockUpdate(String coords, String blockdata) {
        BlockData data = Bukkit.createBlockData(blockdata);
        String[] coords_p = coords.split("=");
        String worldname = coords_p[0];
        int x = Integer.parseInt(coords_p[1]);
        int y = Integer.parseInt(coords_p[2]);
        int z = Integer.parseInt(coords_p[3]);
        new Location(Bukkit.getWorld(worldname), x, y, z).getBlock().setBlockData(data);
    }

    public static void registerChunkUpdate(String Chunkkey, HashMap<String, String> chunk) {
        String[] p = Chunkkey.split("=");
        String worldname = p[0];
        String key = p[1];
        Chunk chunk1 = Objects.requireNonNull(Bukkit.getWorld(worldname)).getChunkAt(Long.parseLong(key));
        add(() -> {
           for(int x = 0; x <= 15; x++) {
               for(int y = -63; y <= 319; y++) {
                   for(int z = 0; z <= 15; z++) {
                       chunk1.getBlock(x, y, z).setBlockData(Bukkit.createBlockData(chunk.get(x + "=" + y + "=" + z)));
                   }
               }
           }
        });
    }

    private static final List<Runnable> runnables = new ArrayList<>();

    public static List<Runnable> getAll() {
        synchronized (runnables) {
            List<Runnable> runnableList = runnables;
            runnables.clear();
            return runnableList;
        }
    }

    public static void add(Runnable runnable) {
        synchronized (runnables) {
            runnables.add(runnable);
        }
    }

    public static boolean hasLock(LevelChunk chunk) {
        return ChunkListener.containsKey(ChunkUtils.getChunkkey(chunk));
    }

    public static void updateChunk(String Chunkkey, LevelChunk chunk) {
        String[] args = Chunkkey.split("=");
        String worldname = args[0];
        long chunkkey_ = Long.parseLong(args[1]);
        Chunk chunk1 = Objects.requireNonNull(Bukkit.getWorld(worldname)).getChunkAt(chunkkey_);
        ChunkSnapshot tempchunk1 = chunk1.getChunkSnapshot();
        ChunkSnapshot tempchunk = chunk.getBukkitChunk().getChunkSnapshot();
        ArrayList<Coords> blocksneedingupdate = new ArrayList<>();
        for(int x = 0;x <= 15; x++) {
            for(int y= -64;y<= 384; y++) {
                for(int z = 0;z <= 15; z++) {
                    if(tempchunk.getBlockData(x, y, z) != tempchunk1.getBlockData(x, y ,z)) {
                        blocksneedingupdate.add(new Coords(x, y, z));
                    }
                }
            }
        }
        Runnable runnable = () -> {
            blocksneedingupdate.forEach(coords -> {
                chunk1.getBlock(coords.getX(), coords.getY(), coords.getZ()).setBlockData(
                    tempchunk.getBlockData(coords.getX(), coords.getY(), coords.getZ()));
            });
        };
        add(runnable);
    }

    public static void AckChunkLock() {

    }

    public static void broadcastonlyToServersListening() {

    }

    public static void addChunkListener(String Chunkkey, String Server) {
        ArrayList<String> servers = new ArrayList<>();
        if(ChunkListener.containsKey(Chunkkey)) {
            servers = ChunkListener.get(Chunkkey);
        }
        servers.add(Server);
        ChunkListener.put(Chunkkey, servers);
    }

    public static void removeChunkListener(String Chunkkey, String Server) {
        if(ChunkListener.containsKey(Chunkkey));
    }

    public static boolean tickChunks() {
        return true;
    }
}
