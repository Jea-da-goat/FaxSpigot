package ac.seven.CDN.Syncher;

import ac.seven.CDN.Lock.LockUtils;
import ac.seven.CDN.Lock.Settings;
import ac.seven.CDN.Server;
import ac.seven.CDN.Syncher.IO.Network.Server.PacketHandler;
import ac.seven.CDN.Syncher.Utils.ChunkUtils;
import ac.seven.CDN.Syncher.Utils.Coords;
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

public class ChunkSyncManager {


    private static HashMap<String, String> ChunkListener = new HashMap<>();
    private static HashMap<String, ArrayList<String>> ServerListener = new HashMap<>();

    private static HashMap<String, String> blockupdates = new HashMap<>();
    private static HashMap<String, HashMap<String, String>> chunkupdates = new HashMap<>();

    private static HashMap<String, String> chunkunloads = new HashMap<>();


    private static LockUtils<String> DataLock = new LockUtils<>();
    public static void addQueue(ChunkHolder holder) {
        LevelChunk chunk = holder.getFullChunk();

    }

    public static void directload(Chunk chunk) {
        loadChunk(ChunkUtils.getChunkkey(chunk), chunk);
    }

    public static void directunload(Chunk chunk) {
        new Thread(() -> {
            String Chunkkey = ChunkUtils.getChunkkey(chunk);
            DataLock.tryOptainLock(Chunkkey);
            chunkunloads.put(Chunkkey, Chunkkey);
            DataLock.releaseLock(Chunkkey);
        }).start();
    }

    public static void ChunkSyncTask() {
        new Thread(() -> {

            while(true) {
                try {

                    if(PacketHandler.getChannels().size() > 0) {
                        HashMap<String, String> blockupdates_temp = new HashMap<>();
                        if (Settings.TickChunks) {
                            blockupdates.forEach((key, value) -> {
                                DataLock.tryOptainLock(key);
                                blockupdates_temp.put(key, value);
                                blockupdates.remove(key);
                                DataLock.releaseLock(key);
                            });
                            HashMap<String, Object> packet = new HashMap<>();
                            packet.put("blockUpdate", blockupdates_temp);
                            PacketHandler.getChannels().forEach(channel -> {
                                channel.writeAndFlush(packet);
                            });
                        } else {
                            blockupdates.forEach((key, value) -> {
                                DataLock.tryOptainLock(key);
                                blockupdates_temp.put(key, value);
                                blockupdates.remove(key);
                                DataLock.releaseLock(key);
                            });
                            HashMap<String, HashMap<String, String>> chunkupdates_temp = new HashMap<>();
                            chunkupdates.forEach((key, value) -> {
                                DataLock.tryOptainLock(key);
                                chunkupdates_temp.put(key, value);
                                chunkupdates.remove(key);
                                DataLock.releaseLock(key);
                            });
                            HashMap<String, String> chunkunloads_temp = new HashMap<>();
                            chunkunloads.forEach((key, value) -> {
                                DataLock.tryOptainLock(key);
                                chunkunloads_temp.put(key, value);
                                chunkunloads.remove(key);
                                DataLock.releaseLock(key);
                            });

                            HashMap<String, Object> packet = new HashMap<>();
                            packet.put("blockUpdate", blockupdates_temp);
                            packet.put("chunkUpdate", chunkupdates_temp);
                            packet.put("unloadChunk", chunkunloads_temp);
                            Server.getClient().getConnection().writeAndFlush(packet);
                        }
                    }
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

    public static void broadcastBlockUpdate(Level world, BlockPos bp, BlockState blockState) {
        new Thread(() -> {
            CraftBlockData blockdata1 = blockState.createCraftBlockData();
            BlockData blockData = blockdata1.clone();
            String coords = world.getWorld().getName() + "=" + bp.getX() + "=" + bp.getY() + "=" + bp.getZ();
            DataLock.tryOptainLock(coords);
            blockupdates.put(coords, blockData.getAsString());
            DataLock.releaseLock(coords);
        }).start();

    }

    public static void registerBlockUpdate(String coords, String blockdata) {
        synchronized (runnables) {
            runnables.add(() -> {
                BlockData data = Bukkit.createBlockData(blockdata);
                String[] coords_p = coords.split("=");
                String worldname = coords_p[0];
                int x = Integer.parseInt(coords_p[1]);
                int y = Integer.parseInt(coords_p[2]);
                int z = Integer.parseInt(coords_p[3]);
                new Location(Bukkit.getWorld(worldname), x, y, z).getBlock().setBlockData(data);
            });
        }
    }

    public static void loadChunk(String Chunkkey, Chunk chunk) {
        new Thread(() -> {
            HashMap<String, String> chunkdata = ChunkUtils.serializeChunk(chunk.getChunkSnapshot());
            DataLock.tryOptainLock(Chunkkey);
            chunkupdates.put(Chunkkey, chunkdata);
            DataLock.releaseLock(Chunkkey);
        }).start();
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
            for(int y = Settings.minY; y <= Settings.maxY; y++) {
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

    public static void ChunkLock() {

    }

    public static void AckChunkLock() {

    }

    public static void ChunkListener(String Chunkkey, String Server) {
        ChunkListener.put(Chunkkey, Server);
    }
}
