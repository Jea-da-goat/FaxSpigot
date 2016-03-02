package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import co.aikar.timings.TimingHistory; // Paper
import co.aikar.timings.Timings; // Paper
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import org.slf4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.generator.CustomWorldChunkManager;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.util.WorldUUID;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.TimeSkipEvent;
// CraftBukkit end
import it.unimi.dsi.fastutil.ints.IntArrayList; // Paper

public class ServerLevel extends Level implements WorldGenLevel {

    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    private static final int MIN_RAIN_DELAY_TIME = 12000;
    private static final int MAX_RAIN_DELAY_TIME = 180000;
    private static final int MIN_RAIN_TIME = 12000;
    private static final int MAX_RAIN_TIME = 24000;
    private static final int MIN_THUNDER_DELAY_TIME = 12000;
    private static final int MAX_THUNDER_DELAY_TIME = 180000;
    private static final int MIN_THUNDER_TIME = 3600;
    private static final int MAX_THUNDER_TIME = 15600;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<ServerPlayer> players;
    public final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    public final PrimaryLevelData serverLevelData; // CraftBukkit - type
    final EntityTickList entityTickList;
    //public final PersistentEntitySectionManager<Entity> entityManager; // Paper - rewrite chunk system
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    private final LevelTicks<Block> blockTicks;
    private final LevelTicks<Fluid> fluidTicks;
    final Set<Mob> navigatingMobs;
    volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    private final List<BlockEventData> blockEventsToReschedule;
    private boolean handlingTick;
    private final List<CustomSpawner> customSpawners;
    @Nullable
    private final EndDragonFight dragonFight;
    final Int2ObjectMap<EnderDragonPart> dragonParts;
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    private final boolean tickTime;

    // CraftBukkit start
    public final LevelStorageSource.LevelStorageAccess convertable;
    public final UUID uuid;

    public LevelChunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunk(x, z, false);
    }

    @Override
    public ResourceKey<LevelStem> getTypeKey() {
        return convertable.dimensionType;
    }

    // Paper start
    public final boolean areChunksLoadedForMove(AABB axisalignedbb) {
        // copied code from collision methods, so that we can guarantee that they wont load chunks (we don't override
        // ICollisionAccess methods for VoxelShapes)
        // be more strict too, add a block (dumb plugins in move events?)
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        ServerChunkCache chunkProvider = this.getChunkSource();

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (chunkProvider.getChunkAtIfLoadedImmediately(cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public final void loadChunksForMoveAsync(AABB axisalignedbb, ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                             java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        if (Thread.currentThread() != this.thread) {
            this.getChunkSource().mainThreadProcessor.execute(() -> {
                this.loadChunksForMoveAsync(axisalignedbb, priority, onLoad);
            });
            return;
        }
        List<net.minecraft.world.level.chunk.ChunkAccess> ret = new java.util.ArrayList<>();
        IntArrayList ticketLevels = new IntArrayList();

        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        ServerChunkCache chunkProvider = this.getChunkSource();

        int requiredChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        int[] loadedChunks = new int[1];

        Long holderIdentifier = Long.valueOf(chunkProvider.chunkFutureAwaitCounter++);

        java.util.function.Consumer<net.minecraft.world.level.chunk.ChunkAccess> consumer = (net.minecraft.world.level.chunk.ChunkAccess chunk) -> {
            if (chunk != null) {
                int ticketLevel = Math.max(33, chunkProvider.chunkMap.getUpdatingChunkIfPresent(chunk.getPos().toLong()).getTicketLevel());
                ret.add(chunk);
                ticketLevels.add(ticketLevel);
                chunkProvider.addTicketAtLevel(TicketType.FUTURE_AWAIT, chunk.getPos(), ticketLevel, holderIdentifier);
            }
            if (++loadedChunks[0] == requiredChunks) {
                try {
                    onLoad.accept(java.util.Collections.unmodifiableList(ret));
                } finally {
                    for (int i = 0, len = ret.size(); i < len; ++i) {
                        ChunkPos chunkPos = ret.get(i).getPos();
                        int ticketLevel = ticketLevels.getInt(i);

                        chunkProvider.addTicketAtLevel(TicketType.UNKNOWN, chunkPos, ticketLevel, chunkPos);
                        chunkProvider.removeTicketAtLevel(TicketType.FUTURE_AWAIT, chunkPos, ticketLevel, holderIdentifier);
                    }
                }
            }
        };

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                io.papermc.paper.chunk.system.ChunkSystem.scheduleChunkLoad(
                    this, cx, cz, net.minecraft.world.level.chunk.ChunkStatus.FULL, true, priority, consumer
                );
            }
        }
    }

    // Paper start - rewrite chunk system
    public final io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler chunkTaskScheduler;
    public final io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController chunkDataControllerNew
        = new io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController(io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.CHUNK_DATA) {

        @Override
        public net.minecraft.world.level.chunk.storage.RegionFileStorage getCache() {
            return ServerLevel.this.getChunkSource().chunkMap.regionFileCache;
        }

        @Override
        public void writeData(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
            ServerLevel.this.getChunkSource().chunkMap.write(new ChunkPos(chunkX, chunkZ), compound);
        }

        @Override
        public net.minecraft.nbt.CompoundTag readData(int chunkX, int chunkZ) throws IOException {
            return ServerLevel.this.getChunkSource().chunkMap.readSync(new ChunkPos(chunkX, chunkZ));
        }
    };
    public final io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController poiDataControllerNew
        = new io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController(io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.POI_DATA) {

        @Override
        public net.minecraft.world.level.chunk.storage.RegionFileStorage getCache() {
            return ServerLevel.this.getChunkSource().chunkMap.getPoiManager();
        }

        @Override
        public void writeData(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
            ServerLevel.this.getChunkSource().chunkMap.getPoiManager().write(new ChunkPos(chunkX, chunkZ), compound);
        }

        @Override
        public net.minecraft.nbt.CompoundTag readData(int chunkX, int chunkZ) throws IOException {
            return ServerLevel.this.getChunkSource().chunkMap.getPoiManager().read(new ChunkPos(chunkX, chunkZ));
        }
    };
    public final io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController entityDataControllerNew
        = new io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController(io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.ENTITY_DATA) {

        @Override
        public net.minecraft.world.level.chunk.storage.RegionFileStorage getCache() {
            return ServerLevel.this.entityStorage;
        }

        @Override
        public void writeData(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
            ServerLevel.this.writeEntityChunk(chunkX, chunkZ, compound);
        }

        @Override
        public net.minecraft.nbt.CompoundTag readData(int chunkX, int chunkZ) throws IOException {
            return ServerLevel.this.readEntityChunk(chunkX, chunkZ);
        }
    };
    private final EntityRegionFileStorage entityStorage;

    private static final class EntityRegionFileStorage extends net.minecraft.world.level.chunk.storage.RegionFileStorage {

        public EntityRegionFileStorage(Path directory, boolean dsync) {
            super(directory, dsync);
        }

        protected void write(ChunkPos pos, net.minecraft.nbt.CompoundTag nbt) throws IOException {
            ChunkPos nbtPos = nbt == null ? null : EntityStorage.readChunkPos(nbt);
            if (nbtPos != null && !pos.equals(nbtPos)) {
                throw new IllegalArgumentException(
                    "Entity chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + pos.toString()
                        + " but compound says coordinate is " + nbtPos + " for world: " + this
                );
            }
            super.write(pos, nbt);
        }
    }

    private void writeEntityChunk(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            io.papermc.paper.chunk.system.io.RegionFileIOThread.scheduleSave(
                this, chunkX, chunkZ, compound,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.ENTITY_DATA);
            return;
        }
        this.entityStorage.write(new ChunkPos(chunkX, chunkZ), compound);
    }

    private net.minecraft.nbt.CompoundTag readEntityChunk(int chunkX, int chunkZ) throws IOException {
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            return io.papermc.paper.chunk.system.io.RegionFileIOThread.loadData(
                this, chunkX, chunkZ, io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.ENTITY_DATA,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
            );
        }
        return this.entityStorage.read(new ChunkPos(chunkX, chunkZ));
    }

    private final io.papermc.paper.chunk.system.entity.EntityLookup entityLookup;
    public final io.papermc.paper.chunk.system.entity.EntityLookup getEntityLookup() {
        return this.entityLookup;
    }
    // Paper end - rewrite chunk system

    // Add env and gen to constructor, IWorldDataServer -> WorldDataServer
    public ServerLevel(MinecraftServer minecraftserver, Executor executor, LevelStorageSource.LevelStorageAccess convertable_conversionsession, PrimaryLevelData iworlddataserver, ResourceKey<Level> resourcekey, LevelStem worlddimension, ChunkProgressListener worldloadlistener, boolean flag, long i, List<CustomSpawner> list, boolean flag1, org.bukkit.World.Environment env, org.bukkit.generator.ChunkGenerator gen, org.bukkit.generator.BiomeProvider biomeProvider) {
        // Holder holder = worlddimension.type(); // CraftBukkit - decompile error
        // Objects.requireNonNull(minecraftserver); // CraftBukkit - decompile error
        super(iworlddataserver, resourcekey, worlddimension.type(), minecraftserver::getProfiler, false, flag, i, minecraftserver.getMaxChainedNeighborUpdates(), gen, biomeProvider, env, spigotConfig -> minecraftserver.paperConfigurations.createWorldConfig(io.papermc.paper.configuration.PaperConfigurations.createWorldContextMap(convertable_conversionsession.levelDirectory.path(), iworlddataserver.getLevelName(), resourcekey.location(), spigotConfig))); // Paper
        this.pvpMode = minecraftserver.isPvpAllowed();
        this.convertable = convertable_conversionsession;
        this.uuid = WorldUUID.getUUID(convertable_conversionsession.levelDirectory.path().toFile());
        // CraftBukkit end
        this.players = Lists.newArrayList();
        this.entityTickList = new EntityTickList();
        this.blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier());
        this.fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier());
        this.navigatingMobs = new ObjectOpenHashSet();
        this.blockEvents = new ObjectLinkedOpenHashSet();
        this.blockEventsToReschedule = new ArrayList(64);
        this.dragonParts = new Int2ObjectOpenHashMap();
        this.tickTime = flag1;
        this.server = minecraftserver;
        this.customSpawners = list;
        this.serverLevelData = iworlddataserver;
        ChunkGenerator chunkgenerator = worlddimension.generator();
        // CraftBukkit start
        this.serverLevelData.setWorld(this);

        if (biomeProvider != null) {
            BiomeSource worldChunkManager = new CustomWorldChunkManager(getWorld(), biomeProvider, this.server.registryAccess().registryOrThrow(Registries.BIOME));
            if (chunkgenerator instanceof NoiseBasedChunkGenerator cga) {
                chunkgenerator = new NoiseBasedChunkGenerator(worldChunkManager, cga.settings);
            } else if (chunkgenerator instanceof FlatLevelSource cpf) {
                chunkgenerator = new FlatLevelSource(cpf.settings(), worldChunkManager);
            }
        }

        if (gen != null) {
            chunkgenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(this, chunkgenerator, gen);
        }
        // CraftBukkit end
        boolean flag2 = minecraftserver.forceSynchronousWrites();
        DataFixer datafixer = minecraftserver.getFixerUpper();
        this.entityStorage = new EntityRegionFileStorage(convertable_conversionsession.getDimensionPath(resourcekey).resolve("entities"), flag2); // Paper - rewrite chunk system  //EntityPersistentStorage<Entity> entitypersistentstorage = new EntityStorage(this, convertable_conversionsession.getDimensionPath(resourcekey).resolve("entities"), datafixer, flag2, minecraftserver);

        // this.entityManager = new PersistentEntitySectionManager<>(Entity.class, new ServerLevel.EntityCallbacks(), entitypersistentstorage, this.entitySliceManager); // Paper // Paper - rewrite chunk system
        StructureTemplateManager structuretemplatemanager = minecraftserver.getStructureManager();
        int j = this.spigotConfig.viewDistance; // Spigot
        int k = this.spigotConfig.simulationDistance; // Spigot
        //PersistentEntitySectionManager persistententitysectionmanager = this.entityManager; // Paper - rewrite chunk system

        //Objects.requireNonNull(this.entityManager); // Paper - rewrite chunk system
        this.chunkSource = new ServerChunkCache(this, convertable_conversionsession, datafixer, structuretemplatemanager, executor, chunkgenerator, j, k, flag2, worldloadlistener, null, () -> { // Paper - rewrite chunk system
            return minecraftserver.overworld().getDataStorage();
        });
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        this.updateSkyBrightness();
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(minecraftserver.getAbsoluteMaxWorldSize());
        this.raids = (Raids) this.getDataStorage().computeIfAbsent((nbttagcompound) -> {
            return Raids.load(this, nbttagcompound);
        }, () -> {
            return new Raids(this);
        }, Raids.getFileId(this.dimensionTypeRegistration()));
        if (!minecraftserver.isSingleplayer()) {
            iworlddataserver.setGameType(minecraftserver.getDefaultGameType());
        }

        long l = minecraftserver.getWorldData().worldGenOptions().seed();

        this.structureCheck = new StructureCheck(this.chunkSource.chunkScanner(), this.registryAccess(), minecraftserver.getStructureManager(), this.getTypeKey(), chunkgenerator, this.chunkSource.randomState(), this, chunkgenerator.getBiomeSource(), l, datafixer); // Paper - Fix missing CB diff
        this.structureManager = new StructureManager(this, this.serverLevelData.worldGenOptions(), this.structureCheck); // CraftBukkit
        if ((this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) || env == org.bukkit.World.Environment.THE_END) { // CraftBukkit - Allow to create EnderDragonBattle in default and custom END
            this.dragonFight = new EndDragonFight(this, this.serverLevelData.worldGenOptions().seed(), this.serverLevelData.endDragonFightData()); // CraftBukkit
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.getCraftServer().addWorld(this.getWorld()); // CraftBukkit

        this.chunkTaskScheduler = new io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler(this, io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler.workerThreads); // Paper - rewrite chunk system
        this.entityLookup = new io.papermc.paper.chunk.system.entity.EntityLookup(this, new EntityCallbacks()); // Paper - rewrite chunk system
    }

    public void setWeatherParameters(int clearDuration, int rainDuration, boolean raining, boolean thundering) {
        this.serverLevelData.setClearWeatherTime(clearDuration);
        this.serverLevelData.setRainTime(rainDuration);
        this.serverLevelData.setThunderTime(rainDuration);
        this.serverLevelData.setRaining(raining);
        this.serverLevelData.setThundering(thundering);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(biomeX, biomeY, biomeZ, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    public void tick(BooleanSupplier shouldKeepTicking) {
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        this.handlingTick = true;
        gameprofilerfiller.push("world border");
        this.getWorldBorder().tick();
        gameprofilerfiller.popPush("weather");
        this.advanceWeatherCycle();
        int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        long j;

        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            // CraftBukkit start
            j = this.levelData.getDayTime() + 24000L;
            TimeSkipEvent event = new TimeSkipEvent(this.getWorld(), TimeSkipEvent.SkipReason.NIGHT_SKIP, (j - j % 24000L) - this.getDayTime());
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                getCraftServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }
            }

            if (!event.isCancelled()) {
                this.wakeUpAllPlayers();
            }
            // CraftBukkit end
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }

        this.updateSkyBrightness();
        this.tickTime();
        gameprofilerfiller.popPush("tickPending");
        timings.scheduledBlocks.startTiming(); // Paper
        if (!this.isDebug()) {
            j = this.getGameTime();
            gameprofilerfiller.push("blockTicks");
            this.blockTicks.tick(j, 65536, this::tickBlock);
            gameprofilerfiller.popPush("fluidTicks");
            this.fluidTicks.tick(j, 65536, this::tickFluid);
            gameprofilerfiller.pop();
        }
        timings.scheduledBlocks.stopTiming(); // Paper

        gameprofilerfiller.popPush("raid");
        this.timings.raids.startTiming(); // Paper - timings
        this.raids.tick();
        this.timings.raids.stopTiming(); // Paper - timings
        gameprofilerfiller.popPush("chunkSource");
        this.timings.chunkProviderTick.startTiming(); // Paper - timings
        this.getChunkSource().tick(shouldKeepTicking, true);
        this.timings.chunkProviderTick.stopTiming(); // Paper - timings
        gameprofilerfiller.popPush("blockEvents");
        timings.doSounds.startTiming(); // Spigot
        this.runBlockEvents();
        timings.doSounds.stopTiming(); // Spigot
        this.handlingTick = false;
        gameprofilerfiller.pop();
        boolean flag = true || !this.players.isEmpty() || !this.getForcedChunks().isEmpty(); // CraftBukkit - this prevents entity cleanup, other issues on servers with no players

        if (flag) {
            this.resetEmptyTime();
        }

        if (flag || this.emptyTime++ < 300) {
            gameprofilerfiller.push("entities");
            timings.tickEntities.startTiming(); // Spigot
            if (this.dragonFight != null) {
                gameprofilerfiller.push("dragonFight");
                this.dragonFight.tick();
                gameprofilerfiller.pop();
            }

            org.spigotmc.ActivationRange.activateEntities(this); // Spigot
            timings.entityTick.startTiming(); // Spigot
            this.entityTickList.forEach((entity) -> {
                if (!entity.isRemoved()) {
                    if (false && this.shouldDiscardEntity(entity)) { // CraftBukkit - We prevent spawning in general, so this butchering is not needed
                        entity.discard();
                    } else {
                        gameprofilerfiller.push("checkDespawn");
                        entity.checkDespawn();
                        gameprofilerfiller.pop();
                        if (true || this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) { // Paper - now always true if in the ticking list
                            Entity entity1 = entity.getVehicle();

                            if (entity1 != null) {
                                if (!entity1.isRemoved() && entity1.hasPassenger(entity)) {
                                    return;
                                }

                                entity.stopRiding();
                            }

                            gameprofilerfiller.push("tick");
                            this.guardEntityTick(this::tickNonPassenger, entity);
                            gameprofilerfiller.pop();
                        }
                    }
                }
            });
            timings.entityTick.stopTiming(); // Spigot
            timings.tickEntities.stopTiming(); // Spigot
            gameprofilerfiller.pop();
            this.tickBlockEntities();
        }

        gameprofilerfiller.push("entityManagement");
        //this.entityManager.tick(); // Paper - rewrite chunk system
        gameprofilerfiller.pop();
    }

    @Override
    public boolean shouldTickBlocksAt(long chunkPos) {
        // Paper start - replace player chunk loader system
        ChunkHolder holder = this.chunkSource.chunkMap.getVisibleChunkIfPresent(chunkPos);
        return holder != null && holder.isTickingReady();
        // Paper end - replace player chunk loader system
    }

    protected void tickTime() {
        if (this.tickTime) {
            long i = this.levelData.getGameTime() + 1L;

            this.serverLevelData.setGameTime(i);
            this.serverLevelData.getScheduledEvents().tick(this.server, i);
            if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }

        }
    }

    public void setDayTime(long timeOfDay) {
        this.serverLevelData.setDayTime(timeOfDay);
    }

    public void tickCustomSpawners(boolean spawnMonsters, boolean spawnAnimals) {
        Iterator iterator = this.customSpawners.iterator();

        while (iterator.hasNext()) {
            CustomSpawner mobspawner = (CustomSpawner) iterator.next();

            mobspawner.tick(this, spawnMonsters, spawnAnimals);
        }

    }

    private boolean shouldDiscardEntity(Entity entity) {
        return !this.server.isSpawningAnimals() && (entity instanceof Animal || entity instanceof WaterAnimal) ? true : !this.server.areNpcsEnabled() && entity instanceof Npc;
    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        (this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList())).forEach((entityplayer) -> { // CraftBukkit - decompile error
            entityplayer.stopSleepInBed(false, false);
        });
    }

    public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        boolean flag = this.isRaining();
        int j = chunkcoordintpair.getMinBlockX();
        int k = chunkcoordintpair.getMinBlockZ();
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        gameprofilerfiller.push("thunder");
        BlockPos blockposition;

        if (flag && this.isThundering() && this.spigotConfig.thunderChance > 0 && this.random.nextInt(this.spigotConfig.thunderChance) == 0) { // Spigot
            blockposition = this.findLightningTargetAround(this.getBlockRandomPos(j, 0, k, 15));
            if (this.isRainingAt(blockposition)) {
                DifficultyInstance difficultydamagescaler = this.getCurrentDifficultyAt(blockposition);
                boolean flag1 = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && this.random.nextDouble() < (double) difficultydamagescaler.getEffectiveDifficulty() * 0.01D && !this.getBlockState(blockposition.below()).is(Blocks.LIGHTNING_ROD);

                if (flag1) {
                    SkeletonHorse entityhorseskeleton = (SkeletonHorse) EntityType.SKELETON_HORSE.create(this);

                    if (entityhorseskeleton != null) {
                        entityhorseskeleton.setTrap(true);
                        entityhorseskeleton.setAge(0);
                        entityhorseskeleton.setPos((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                        this.addFreshEntity(entityhorseskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
                    }
                }

                LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(this);

                if (entitylightning != null) {
                    entitylightning.moveTo(Vec3.atBottomCenterOf(blockposition));
                    entitylightning.setVisualOnly(flag1);
                    this.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER); // CraftBukkit
                }
            }
        }

        gameprofilerfiller.popPush("iceandsnow");
        int l;

        if (this.random.nextInt(16) == 0) {
            blockposition = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, this.getBlockRandomPos(j, 0, k, 15));
            BlockPos blockposition1 = blockposition.below();
            Biome biomebase = (Biome) this.getBiome(blockposition).value();

            if (biomebase.shouldFreeze(this, blockposition1)) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, Blocks.ICE.defaultBlockState(), null); // CraftBukkit
            }

            if (flag) {
                int i1 = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
                BlockState iblockdata;

                if (i1 > 0 && biomebase.shouldSnow(this, blockposition)) {
                    iblockdata = this.getBlockState(blockposition);
                    if (iblockdata.is(Blocks.SNOW)) {
                        l = (Integer) iblockdata.getValue(SnowLayerBlock.LAYERS);
                        if (l < Math.min(i1, 8)) {
                            BlockState iblockdata1 = (BlockState) iblockdata.setValue(SnowLayerBlock.LAYERS, l + 1);

                            Block.pushEntitiesUp(iblockdata, iblockdata1, this, blockposition);
                            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition, iblockdata1, null); // CraftBukkit
                        }
                    } else {
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition, Blocks.SNOW.defaultBlockState(), null); // CraftBukkit
                    }
                }

                iblockdata = this.getBlockState(blockposition1);
                Biome.Precipitation biomebase_precipitation = biomebase.getPrecipitation();

                if (biomebase_precipitation == Biome.Precipitation.RAIN && biomebase.coldEnoughToSnow(blockposition1)) {
                    biomebase_precipitation = Biome.Precipitation.SNOW;
                }

                iblockdata.getBlock().handlePrecipitation(iblockdata, this, blockposition1, biomebase_precipitation);
            }
        }

        gameprofilerfiller.popPush("tickBlocks");
        timings.chunkTicksBlocks.startTiming(); // Paper
        if (randomTickSpeed > 0) {
            LevelChunkSection[] achunksection = chunk.getSections();
            int j1 = achunksection.length;

            for (int k1 = 0; k1 < j1; ++k1) {
                LevelChunkSection chunksection = achunksection[k1];

                if (chunksection.isRandomlyTicking()) {
                    int l1 = chunksection.bottomBlockY();

                    for (l = 0; l < randomTickSpeed; ++l) {
                        BlockPos blockposition2 = this.getBlockRandomPos(j, l1, k, 15);

                        gameprofilerfiller.push("randomTick");
                        BlockState iblockdata2 = chunksection.getBlockState(blockposition2.getX() - j, blockposition2.getY() - l1, blockposition2.getZ() - k);

                        if (iblockdata2.isRandomlyTicking()) {
                            iblockdata2.randomTick(this, blockposition2, this.random);
                        }

                        FluidState fluid = iblockdata2.getFluidState();

                        if (fluid.isRandomlyTicking()) {
                            fluid.randomTick(this, blockposition2, this.random);
                        }

                        gameprofilerfiller.pop();
                    }
                }
            }
        }

        timings.chunkTicksBlocks.stopTiming(); // Paper
        gameprofilerfiller.pop();
    }

    public Optional<BlockPos> findLightningRod(BlockPos pos) {
        Optional<BlockPos> optional = this.getPoiManager().findClosest((holder) -> {
            return holder.is(PoiTypes.LIGHTNING_ROD);
        }, (blockposition1) -> {
            return blockposition1.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockposition1.getX(), blockposition1.getZ()) - 1;
        }, pos, 128, PoiManager.Occupancy.ANY);

        return optional.map((blockposition1) -> {
            return blockposition1.above(1);
        });
    }

    protected BlockPos findLightningTargetAround(BlockPos pos) {
        BlockPos blockposition1 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        Optional<BlockPos> optional = this.findLightningRod(blockposition1);

        if (optional.isPresent()) {
            return (BlockPos) optional.get();
        } else {
            AABB axisalignedbb = (new AABB(blockposition1, new BlockPos(blockposition1.getX(), this.getMaxBuildHeight(), blockposition1.getZ()))).inflate(3.0D);
            List<LivingEntity> list = this.getEntitiesOfClass(LivingEntity.class, axisalignedbb, (entityliving) -> {
                return entityliving != null && entityliving.isAlive() && this.canSeeSky(entityliving.blockPosition());
            });

            if (!list.isEmpty()) {
                return ((LivingEntity) list.get(this.random.nextInt(list.size()))).blockPosition();
            } else {
                if (blockposition1.getY() == this.getMinBuildHeight() - 1) {
                    blockposition1 = blockposition1.above(2);
                }

                return blockposition1;
            }
        }
    }

    public boolean isHandlingTick() {
        return this.handlingTick;
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
                MutableComponent ichatmutablecomponent;

                if (this.sleepStatus.areEnoughSleeping(i)) {
                    ichatmutablecomponent = Component.translatable("sleep.skipping_night");
                } else {
                    ichatmutablecomponent = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                Iterator iterator = this.players.iterator();

                while (iterator.hasNext()) {
                    ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                    entityplayer.displayClientMessage(ichatmutablecomponent, true);
                }

            }
        }
    }

    public void updateSleepingPlayerList() {
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }

    }

    @Override
    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    private void advanceWeatherCycle() {
        boolean flag = this.isRaining();

        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int i = this.serverLevelData.getClearWeatherTime();
                int j = this.serverLevelData.getThunderTime();
                int k = this.serverLevelData.getRainTime();
                boolean flag1 = this.levelData.isThundering();
                boolean flag2 = this.levelData.isRaining();

                if (i > 0) {
                    --i;
                    j = flag1 ? 0 : 1;
                    k = flag2 ? 0 : 1;
                    flag1 = false;
                    flag2 = false;
                } else {
                    if (j > 0) {
                        --j;
                        if (j == 0) {
                            flag1 = !flag1;
                        }
                    } else if (flag1) {
                        j = Mth.randomBetweenInclusive(this.random, 3600, 15600);
                    } else {
                        j = Mth.randomBetweenInclusive(this.random, 12000, 180000);
                    }

                    if (k > 0) {
                        --k;
                        if (k == 0) {
                            flag2 = !flag2;
                        }
                    } else if (flag2) {
                        k = Mth.randomBetweenInclusive(this.random, 12000, 24000);
                    } else {
                        k = Mth.randomBetweenInclusive(this.random, 12000, 180000);
                    }
                }

                this.serverLevelData.setThunderTime(j);
                this.serverLevelData.setRainTime(k);
                this.serverLevelData.setClearWeatherTime(i);
                this.serverLevelData.setThundering(flag1);
                this.serverLevelData.setRaining(flag2);
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        /* CraftBukkit start
        if (this.oRainLevel != this.rainLevel) {
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }
        // */
        for (int idx = 0; idx < this.players.size(); ++idx) {
            if (((ServerPlayer) this.players.get(idx)).level == this) {
                ((ServerPlayer) this.players.get(idx)).tickWeather();
            }
        }

        if (flag != this.isRaining()) {
            // Only send weather packets to those affected
            for (int idx = 0; idx < this.players.size(); ++idx) {
                if (((ServerPlayer) this.players.get(idx)).level == this) {
                    ((ServerPlayer) this.players.get(idx)).setPlayerWeather((!flag ? WeatherType.DOWNFALL : WeatherType.CLEAR), false);
                }
            }
        }
        for (int idx = 0; idx < this.players.size(); ++idx) {
            if (((ServerPlayer) this.players.get(idx)).level == this) {
                ((ServerPlayer) this.players.get(idx)).updateWeather(this.oRainLevel, this.rainLevel, this.oThunderLevel, this.thunderLevel);
            }
        }
        // CraftBukkit end

    }

    private void resetWeatherCycle() {
        // CraftBukkit start
        this.serverLevelData.setRaining(false);
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isRaining()) {
            this.serverLevelData.setRainTime(0);
        }
        // CraftBukkit end
        this.serverLevelData.setThundering(false);
        // CraftBukkit start
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isThundering()) {
            this.serverLevelData.setThunderTime(0);
        }
        // CraftBukkit end
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos pos, Fluid fluid) {
        FluidState fluid1 = this.getFluidState(pos);

        if (fluid1.is(fluid)) {
            fluid1.tick(this, pos);
        }

    }

    private void tickBlock(BlockPos pos, Block block) {
        BlockState iblockdata = this.getBlockState(pos);

        if (iblockdata.is(block)) {
            iblockdata.tick(this, pos, this.random);
        }

    }

    public void tickNonPassenger(Entity entity) {
        ++TimingHistory.entityTicks; // Paper - timings
        // Spigot start
        co.aikar.timings.Timing timer; // Paper
        if (!org.spigotmc.ActivationRange.checkIfActive(entity)) {
            entity.tickCount++;
            timer = entity.getType().inactiveTickTimer.startTiming(); try { // Paper - timings
            entity.inactiveTick();
            } finally { timer.stopTiming(); } // Paper
            return;
        }
        // Spigot end
        // Paper start- timings
        TimingHistory.activatedEntityTicks++;
        timer = entity.getVehicle() != null ? entity.getType().passengerTickTimer.startTiming() : entity.getType().tickTimer.startTiming();
        try {
        // Paper end - timings
        entity.setOldPosAndRot();
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        ++entity.tickCount;
        this.getProfiler().push(() -> {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        });
        gameprofilerfiller.incrementCounter("tickNonPassenger");
        entity.tick();
        entity.postTick(); // CraftBukkit
        this.getProfiler().pop();
        Iterator iterator = entity.getPassengers().iterator();

        while (iterator.hasNext()) {
            Entity entity1 = (Entity) iterator.next();

            this.tickPassenger(entity, entity1);
        }
        } finally { timer.stopTiming(); } // Paper - timings

    }

    private void tickPassenger(Entity vehicle, Entity passenger) {
        if (!passenger.isRemoved() && passenger.getVehicle() == vehicle) {
            if (passenger instanceof Player || this.entityTickList.contains(passenger)) {
                passenger.setOldPosAndRot();
                ++passenger.tickCount;
                ProfilerFiller gameprofilerfiller = this.getProfiler();

                gameprofilerfiller.push(() -> {
                    return BuiltInRegistries.ENTITY_TYPE.getKey(passenger.getType()).toString();
                });
                gameprofilerfiller.incrementCounter("tickPassenger");
                passenger.rideTick();
                passenger.postTick(); // CraftBukkit
                gameprofilerfiller.pop();
                Iterator iterator = passenger.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity2 = (Entity) iterator.next();

                    this.tickPassenger(passenger, entity2);
                }

            }
        } else {
            passenger.stopRiding();
        }
    }

    @Override
    public boolean mayInteract(Player player, BlockPos pos) {
        return !this.server.isUnderSpawnProtection(this, pos, player) && this.getWorldBorder().isWithinBounds(pos);
    }

    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled) {
        // Paper start - rewrite chunk system - add close param
        this.save(progressListener, flush, savingDisabled, false);
    }
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
        // Paper end - rewrite chunk system - add close param
        ServerChunkCache chunkproviderserver = this.getChunkSource();

        if (!savingDisabled) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(getWorld())); // CraftBukkit
            try (co.aikar.timings.Timing ignored = timings.worldSave.startTiming()) { // Paper
            if (progressListener != null) {
                progressListener.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData();
            if (progressListener != null) {
                progressListener.progressStage(Component.translatable("menu.savingChunks"));
            }

                timings.worldSaveChunks.startTiming(); // Paper
            if (!close) chunkproviderserver.save(flush); // Paper - rewrite chunk system
            if (close) chunkproviderserver.close(true); // Paper - rewrite chunk system
                timings.worldSaveChunks.stopTiming(); // Paper
            }// Paper
            // Paper - rewrite chunk system - entity saving moved into ChunkHolder

        } else if (close) { chunkproviderserver.close(false); } // Paper - rewrite chunk system

        // CraftBukkit start - moved from MinecraftServer.saveChunks
        ServerLevel worldserver1 = this;

        this.serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save());
        this.convertable.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        // CraftBukkit end
    }

    private void saveLevelData() {
        if (this.dragonFight != null) {
            this.serverLevelData.setEndDragonFightData(this.dragonFight.saveData()); // CraftBukkit
        }

        this.getChunkSource().getDataStorage().save();
    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();

        this.getEntities(filter, predicate, (List) list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result) {
        this.getEntities(filter, predicate, result, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result, int limit) {
        this.getEntities().get(filter, (entity) -> {
            if (predicate.test(entity)) {
                result.add(entity);
                if (result.size() >= limit) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities((EntityTypeTest) EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
        return this.getPlayers(predicate, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int limit) {
        List<ServerPlayer> list = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (predicate.test(entityplayer)) {
                list.add(entityplayer);
                if (list.size() >= limit) {
                    return list;
                }
            }
        }

        return list;
    }

    @Nullable
    public ServerPlayer getRandomPlayer() {
        List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public boolean addWithUUID(Entity entity) {
        // CraftBukkit start
        return this.addWithUUID(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean addWithUUID(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringTeleport(Entity entity) {
        // CraftBukkit start
        // SPIGOT-6415: Don't call spawn event for entities which travel trough worlds,
        // since it is only an implementation detail, that a new entity is created when
        // they are traveling between worlds.
        this.addDuringTeleport(entity, null);
    }

    public void addDuringTeleport(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringCommandTeleport(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addDuringPortalTeleport(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addNewPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addRespawnedPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    private void addPlayer(ServerPlayer player) {
        Entity entity = (Entity) this.getEntities().get(player.getUUID());

        if (entity != null) {
            ServerLevel.LOGGER.warn("Force-added player with duplicate UUID {}", player.getUUID().toString());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer) entity, Entity.RemovalReason.DISCARDED);
        }

        this.entityLookup.addNewEntity(player); // Paper - rewite chunk system
    }

    // CraftBukkit start
    private boolean addEntity(Entity entity, CreatureSpawnEvent.SpawnReason spawnReason) {
        org.spigotmc.AsyncCatcher.catchOp("entity add"); // Spigot
        if (entity.isRemoved()) {
            // WorldServer.LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityTypes.getKey(entity.getType())); // CraftBukkit
            return false;
        } else {
            // SPIGOT-6415: Don't call spawn event when reason is null. For example when an entity teleports to a new world.
            if (spawnReason != null && !CraftEventFactory.doEntityAddEventCalling(this, entity, spawnReason)) {
                return false;
            }
            // CraftBukkit end

            return this.entityLookup.addNewEntity(entity); // Paper - rewrite chunk system
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        // CraftBukkit start
        return this.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        Stream<UUID> stream = entity.getSelfAndPassengers().map(Entity::getUUID); // CraftBukkit - decompile error
        //PersistentEntitySectionManager persistententitysectionmanager = this.entityManager; // Paper - rewrite chunk system

        //Objects.requireNonNull(this.entityManager); // Paper - rewrite chunk system
        if (stream.anyMatch(this.entityLookup::hasEntity)) { // Paper - rewrite chunk system
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity, reason); // CraftBukkit
            return true;
        }
    }

    public void unload(LevelChunk chunk) {
        // Spigot Start
        for (net.minecraft.world.level.block.entity.BlockEntity tileentity : chunk.getBlockEntities().values()) {
            if (tileentity instanceof net.minecraft.world.Container) {
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((net.minecraft.world.Container) tileentity).getViewers())) {
                    h.closeInventory();
                }
            }
        }
        // Spigot End
        chunk.clearAllBlockEntities();
        chunk.unregisterTickContainerFromLevel(this);
    }

    public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        player.remove(reason);
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, LightningStrikeEvent.Cause cause) {
        LightningStrikeEvent lightning = CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);

        if (lightning.isCancelled()) {
            return false;
        }

        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int entityId, BlockPos pos, int progress) {
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        // CraftBukkit start
        Player entityhuman = null;
        Entity entity = this.getEntity(entityId);
        if (entity instanceof Player) entityhuman = (Player) entity;
        // CraftBukkit end

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer != null && entityplayer.level == this && entityplayer.getId() != entityId) {
                double d0 = (double) pos.getX() - entityplayer.getX();
                double d1 = (double) pos.getY() - entityplayer.getY();
                double d2 = (double) pos.getZ() - entityplayer.getZ();

                // CraftBukkit start
                if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end

                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0D) {
                    entityplayer.connection.send(new ClientboundBlockDestructionPacket(entityId, pos, progress));
                }
            }
        }

    }

    @Override
    public void playSeededSound(@Nullable Player except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.server.getPlayerList().broadcast(except, x, y, z, (double) ((SoundEvent) sound.value()).getRange(volume), this.dimension(), new ClientboundSoundPacket(sound, category, x, y, z, volume, pitch, seed));
    }

    @Override
    public void playSeededSound(@Nullable Player except, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.server.getPlayerList().broadcast(except, entity.getX(), entity.getY(), entity.getZ(), (double) ((SoundEvent) sound.value()).getRange(volume), this.dimension(), new ClientboundSoundEntityPacket(sound, category, entity, volume, pitch, seed));
    }

    @Override
    public void globalLevelEvent(int eventId, BlockPos pos, int data) {
        if (this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().broadcastAll(new ClientboundLevelEventPacket(eventId, pos, data, true));
        } else {
            this.levelEvent((Player) null, eventId, pos, data);
        }

    }

    @Override
    public void levelEvent(@Nullable Player player, int eventId, BlockPos pos, int data) {
        this.server.getPlayerList().broadcast(player, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), 64.0D, this.dimension(), new ClientboundLevelEventPacket(eventId, pos, data, false));
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(GameEvent event, Vec3 emitterPos, GameEvent.Context emitter) {
        this.gameEventDispatcher.post(event, emitterPos, emitter);
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (this.isUpdatingNavigations) {
            String s = "recursive call to sendBlockUpdated";

            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        VoxelShape voxelshape = oldState.getCollisionShape(this, pos);
        VoxelShape voxelshape1 = newState.getCollisionShape(this, pos);

        if (Shapes.joinIsNotEmpty(voxelshape, voxelshape1, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList();
            Iterator iterator = this.navigatingMobs.iterator();

            while (iterator.hasNext()) {
                // CraftBukkit start - fix SPIGOT-6362
                Mob entityinsentient;
                try {
                    entityinsentient = (Mob) iterator.next();
                } catch (java.util.ConcurrentModificationException ex) {
                    // This can happen because the pathfinder update below may trigger a chunk load, which in turn may cause more navigators to register
                    // In this case we just run the update again across all the iterators as the chunk will then be loaded
                    // As this is a relative edge case it is much faster than copying navigators (on either read or write)
                    this.sendBlockUpdated(pos, oldState, newState, flags);
                    return;
                }
                // CraftBukkit end
                PathNavigation navigationabstract = entityinsentient.getNavigation();

                if (navigationabstract.shouldRecomputePath(pos)) {
                    list.add(navigationabstract);
                }
            }

            try {
                this.isUpdatingNavigations = true;
                iterator = list.iterator();

                while (iterator.hasNext()) {
                    PathNavigation navigationabstract1 = (PathNavigation) iterator.next();

                    navigationabstract1.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }

        }
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block sourceBlock) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, (Direction) null);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block sourceBlock, Direction direction) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, direction);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {
        this.neighborUpdater.neighborChanged(pos, sourceBlock, sourcePos);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        this.neighborUpdater.neighborChanged(state, pos, sourceBlock, sourcePos, notify);
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte status) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundEntityEventPacket(entity, status));
    }

    @Override
    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        Explosion explosion = this.explode(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, false);
        // CraftBukkit start
        if (explosion.wasCanceled) {
            return explosion;
        }
        // CraftBukkit end

        if (!explosion.interactsWithBlocks()) {
            explosion.clearToBlow();
        }

        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.distanceToSqr(x, y, z) < 4096.0D) {
                entityplayer.connection.send(new ClientboundExplodePacket(x, y, z, power, explosion.getToBlow(), (Vec3) explosion.getHitPlayers().get(entityplayer)));
            }
        }

        return explosion;
    }

    @Override
    public void blockEvent(BlockPos pos, Block block, int type, int data) {
        this.blockEvents.add(new BlockEventData(pos, block, type, data));
    }

    private void runBlockEvents() {
        this.blockEventsToReschedule.clear();

        while (!this.blockEvents.isEmpty()) {
            BlockEventData blockactiondata = (BlockEventData) this.blockEvents.removeFirst();

            if (this.shouldTickBlocksAt(blockactiondata.pos())) {
                if (this.doBlockEvent(blockactiondata)) {
                    this.server.getPlayerList().broadcast((Player) null, (double) blockactiondata.pos().getX(), (double) blockactiondata.pos().getY(), (double) blockactiondata.pos().getZ(), 64.0D, this.dimension(), new ClientboundBlockEventPacket(blockactiondata.pos(), blockactiondata.block(), blockactiondata.paramA(), blockactiondata.paramB()));
                }
            } else {
                this.blockEventsToReschedule.add(blockactiondata);
            }
        }

        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

    private boolean doBlockEvent(BlockEventData event) {
        BlockState iblockdata = this.getBlockState(event.pos());

        return iblockdata.is(event.block()) ? iblockdata.triggerEvent(this, event.pos(), event.paramA(), event.paramB()) : false;
    }

    @Override
    public LevelTicks<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTicks<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed) {
        // CraftBukkit - visibility api support
        return this.sendParticles(null, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed, false);
    }

    public <T extends ParticleOptions> int sendParticles(ServerPlayer sender, T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6, boolean force) {
        ClientboundLevelParticlesPacket packetplayoutworldparticles = new ClientboundLevelParticlesPacket(t0, force, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);
        // CraftBukkit end
        int j = 0;

        for (int k = 0; k < this.players.size(); ++k) {
            ServerPlayer entityplayer = (ServerPlayer) this.players.get(k);
            if (sender != null && !entityplayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit

            if (this.sendParticles(entityplayer, force, d0, d1, d2, packetplayoutworldparticles)) { // CraftBukkit
                ++j;
            }
        }

        return j;
    }

    public <T extends ParticleOptions> boolean sendParticles(ServerPlayer viewer, T particle, boolean force, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(particle, force, x, y, z, (float) deltaX, (float) deltaY, (float) deltaZ, (float) speed, count);

        return this.sendParticles(viewer, force, x, y, z, packet);
    }

    private boolean sendParticles(ServerPlayer player, boolean force, double x, double y, double z, Packet<?> packet) {
        if (player.getLevel() != this) {
            return false;
        } else {
            BlockPos blockposition = player.blockPosition();

            if (blockposition.closerToCenterThan(new Vec3(x, y, z), force ? 512.0D : 32.0D)) {
                player.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntity(int id) {
        return (Entity) this.getEntities().get(id);
    }

    /** @deprecated */
    @Deprecated
    @Nullable
    public Entity getEntityOrPart(int id) {
        Entity entity = (Entity) this.getEntities().get(id);

        return entity != null ? entity : (Entity) this.dragonParts.get(id);
    }

    @Nullable
    public Entity getEntity(UUID uuid) {
        return (Entity) this.getEntities().get(uuid);
    }

    @Nullable
    public BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipReferencedStructures) {
        if (!this.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(structureTag);

            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource().getGenerator().findNearestMapStructure(this, (HolderSet) optional.get(), pos, radius, skipReferencedStructures);

                return pair != null ? (BlockPos) pair.getFirst() : null;
            }
        }
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(Predicate<Holder<Biome>> predicate, BlockPos pos, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval) {
        return this.getChunkSource().getGenerator().getBiomeSource().findClosestBiome3d(pos, radius, horizontalBlockCheckInterval, verticalBlockCheckInterval, predicate, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.server.getRecipeManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.server.registryAccess();
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(String id) {
        // CraftBukkit start
        return (MapItemSavedData) this.getServer().overworld().getDataStorage().get((nbttagcompound) -> {
            // We only get here when the data file exists, but is not a valid map
            MapItemSavedData newMap = MapItemSavedData.load(nbttagcompound);
            newMap.id = id;
            MapInitializeEvent event = new MapInitializeEvent(newMap.mapView);
            Bukkit.getServer().getPluginManager().callEvent(event);
            return newMap;
        }, id);
        // CraftBukkit end
    }

    @Override
    public void setMapData(String id, MapItemSavedData state) {
        state.id = id; // CraftBukkit
        this.getServer().overworld().getDataStorage().set(id, state);
    }

    @Override
    public int getFreeMapId() {
        return ((MapIndex) this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex::load, MapIndex::new, "idcounts")).getFreeAuxValueForMap();
    }

    public void setDefaultSpawnPos(BlockPos pos, float angle) {
        ChunkPos chunkcoordintpair = new ChunkPos(new BlockPos(this.levelData.getXSpawn(), 0, this.levelData.getZSpawn()));

        this.levelData.setSpawn(pos, angle);
        this.getChunkSource().removeRegionTicket(TicketType.START, chunkcoordintpair, 11, Unit.INSTANCE);
        this.getChunkSource().addRegionTicket(TicketType.START, new ChunkPos(pos), 11, Unit.INSTANCE);
        this.getServer().getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(pos, angle));
    }

    public LongSet getForcedChunks() {
        ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) this.getDataStorage().get(ForcedChunksSavedData::load, "chunks");

        return (LongSet) (forcedchunk != null ? LongSets.unmodifiable(forcedchunk.getChunks()) : LongSets.EMPTY_SET);
    }

    public boolean setChunkForced(int x, int z, boolean forced) {
        ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) this.getDataStorage().computeIfAbsent(ForcedChunksSavedData::load, ForcedChunksSavedData::new, "chunks");
        ChunkPos chunkcoordintpair = new ChunkPos(x, z);
        long k = chunkcoordintpair.toLong();
        boolean flag1;

        if (forced) {
            flag1 = forcedchunk.getChunks().add(k);
            if (flag1) {
                this.getChunk(x, z);
            }
        } else {
            flag1 = forcedchunk.getChunks().remove(k);
        }

        forcedchunk.setDirty(flag1);
        if (flag1) {
            this.getChunkSource().updateChunkForced(chunkcoordintpair, forced);
        }

        return flag1;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void onBlockStateChange(BlockPos pos, BlockState oldBlock, BlockState newBlock) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(oldBlock);
        Optional<Holder<PoiType>> optional1 = PoiTypes.forState(newBlock);

        if (!Objects.equals(optional, optional1)) {
            BlockPos blockposition1 = pos.immutable();

            optional.ifPresent((holder) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().remove(blockposition1);
                    DebugPackets.sendPoiRemovedPacket(this, blockposition1);
                });
            });
            optional1.ifPresent((holder) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().add(blockposition1, holder);
                    DebugPackets.sendPoiAddedPacket(this, blockposition1);
                });
            });
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos pos) {
        return this.isCloseToVillage(pos, 1);
    }

    public boolean isVillage(SectionPos sectionPos) {
        return this.isVillage(sectionPos.center());
    }

    public boolean isCloseToVillage(BlockPos pos, int maxDistance) {
        return maxDistance > 6 ? false : this.sectionsToVillage(SectionPos.of(pos)) <= maxDistance;
    }

    public int sectionsToVillage(SectionPos pos) {
        return this.getPoiManager().sectionsToVillage(pos);
    }

    public Raids getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPos pos) {
        return this.raids.getNearbyRaid(pos, 9216);
    }

    public boolean isRaided(BlockPos pos) {
        return this.getRaidAt(pos) != null;
    }

    public void onReputationEvent(ReputationEventType interaction, Entity entity, ReputationEventHandler observer) {
        observer.onReputationEventFrom(interaction, entity);
    }

    public void saveDebugReport(Path path) throws IOException {
        ChunkMap playerchunkmap = this.getChunkSource().chunkMap;
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path.resolve("stats.txt"));

        try {
            bufferedwriter.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", playerchunkmap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState spawnercreature_d = this.getChunkSource().getLastSpawnState();

            if (spawnercreature_d != null) {
                ObjectIterator objectiterator = spawnercreature_d.getMobCategoryCounts().object2IntEntrySet().iterator();

                while (objectiterator.hasNext()) {
                    Entry<MobCategory> entry = (Entry) objectiterator.next();

                    bufferedwriter.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", ((MobCategory) entry.getKey()).getName(), entry.getIntValue()));
                }
            }

            bufferedwriter.write(String.format(Locale.ROOT, "entities: %s\n", this.entityLookup.getDebugInfo())); // Paper - rewrite chunk system
            bufferedwriter.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
            bufferedwriter.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            bufferedwriter.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            bufferedwriter.write("distance_manager: " + playerchunkmap.getDistanceManager().getDebugStatus() + "\n");
            bufferedwriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

        CrashReport crashreport = new CrashReport("Level dump", new Exception("dummy"));

        this.fillReportDetails(crashreport);
        BufferedWriter bufferedwriter1 = Files.newBufferedWriter(path.resolve("example_crash.txt"));

        try {
            bufferedwriter1.write(crashreport.getFriendlyReport());
        } catch (Throwable throwable2) {
            if (bufferedwriter1 != null) {
                try {
                    bufferedwriter1.close();
                } catch (Throwable throwable3) {
                    throwable2.addSuppressed(throwable3);
                }
            }

            throw throwable2;
        }

        if (bufferedwriter1 != null) {
            bufferedwriter1.close();
        }

        Path path1 = path.resolve("chunks.csv");
        BufferedWriter bufferedwriter2 = Files.newBufferedWriter(path1);

        try {
            //playerchunkmap.dumpChunks(bufferedwriter2); // Paper - rewrite chunk system
        } catch (Throwable throwable4) {
            if (bufferedwriter2 != null) {
                try {
                    bufferedwriter2.close();
                } catch (Throwable throwable5) {
                    throwable4.addSuppressed(throwable5);
                }
            }

            throw throwable4;
        }

        if (bufferedwriter2 != null) {
            bufferedwriter2.close();
        }

        Path path2 = path.resolve("entity_chunks.csv");
        BufferedWriter bufferedwriter3 = Files.newBufferedWriter(path2);

        try {
            //this.entityManager.dumpSections(bufferedwriter3); // Paper - rewrite chunk system
        } catch (Throwable throwable6) {
            if (bufferedwriter3 != null) {
                try {
                    bufferedwriter3.close();
                } catch (Throwable throwable7) {
                    throwable6.addSuppressed(throwable7);
                }
            }

            throw throwable6;
        }

        if (bufferedwriter3 != null) {
            bufferedwriter3.close();
        }

        Path path3 = path.resolve("entities.csv");
        BufferedWriter bufferedwriter4 = Files.newBufferedWriter(path3);

        try {
            ServerLevel.dumpEntities(bufferedwriter4, this.getEntities().getAll());
        } catch (Throwable throwable8) {
            if (bufferedwriter4 != null) {
                try {
                    bufferedwriter4.close();
                } catch (Throwable throwable9) {
                    throwable8.addSuppressed(throwable9);
                }
            }

            throw throwable8;
        }

        if (bufferedwriter4 != null) {
            bufferedwriter4.close();
        }

        Path path4 = path.resolve("block_entities.csv");
        BufferedWriter bufferedwriter5 = Files.newBufferedWriter(path4);

        try {
            this.dumpBlockEntityTickers(bufferedwriter5);
        } catch (Throwable throwable10) {
            if (bufferedwriter5 != null) {
                try {
                    bufferedwriter5.close();
                } catch (Throwable throwable11) {
                    throwable10.addSuppressed(throwable11);
                }
            }

            throw throwable10;
        }

        if (bufferedwriter5 != null) {
            bufferedwriter5.close();
        }

    }

    private static void dumpEntities(Writer writer, Iterable<Entity> entities) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("uuid").addColumn("type").addColumn("alive").addColumn("display_name").addColumn("custom_name").build(writer);
        Iterator iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();
            Component ichatbasecomponent = entity.getCustomName();
            Component ichatbasecomponent1 = entity.getDisplayName();

            csvwriter.writeRow(entity.getX(), entity.getY(), entity.getZ(), entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.isAlive(), ichatbasecomponent1.getString(), ichatbasecomponent != null ? ichatbasecomponent.getString() : null);
        }

    }

    private void dumpBlockEntityTickers(Writer writer) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(writer);
        Iterator iterator = this.blockEntityTickers.iterator();

        while (iterator.hasNext()) {
            TickingBlockEntity tickingblockentity = (TickingBlockEntity) iterator.next();
            BlockPos blockposition = tickingblockentity.getPos();

            csvwriter.writeRow(blockposition.getX(), blockposition.getY(), blockposition.getZ(), tickingblockentity.getType());
        }

    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox box) {
        this.blockEvents.removeIf((blockactiondata) -> {
            return box.isInside(blockactiondata.pos());
        });
    }

    @Override
    public void blockUpdated(BlockPos pos, Block block) {
        if (!this.isDebug()) {
            // CraftBukkit start
            if (populating) {
                return;
            }
            // CraftBukkit end
            this.updateNeighborsAt(pos, block);
        }

    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.serverLevelData.isFlatWorld(); // CraftBukkit
    }

    @Override
    public long getSeed() {
        return this.serverLevelData.worldGenOptions().seed(); // CraftBukkit
    }

    @Nullable
    public EndDragonFight dragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(Locale.ROOT, "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s", this.players.size(), this.entityLookup.getDebugInfo(), ServerLevel.getTypeCount(this.entityLookup.getAll(), (entity) -> { // Paper - rewrite chunk system
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }), this.blockEntityTickers.size(), ServerLevel.getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType), this.getBlockTicks().count(), this.getFluidTicks().count(), this.gatherChunkSourceStats());
    }

    private static <T> String getTypeCount(Iterable<T> items, Function<T, String> classifier) {
        try {
            Object2IntOpenHashMap<String> object2intopenhashmap = new Object2IntOpenHashMap();
            Iterator<T> iterator = items.iterator(); // CraftBukkit - decompile error

            while (iterator.hasNext()) {
                T t0 = iterator.next();
                String s = (String) classifier.apply(t0);

                object2intopenhashmap.addTo(s, 1);
            }

            return (String) object2intopenhashmap.object2IntEntrySet().stream().sorted(Comparator.comparing(Entry<String>::getIntValue).reversed()).limit(5L).map((entry) -> { // CraftBukkit - decompile error
                String s1 = (String) entry.getKey();

                return s1 + ":" + entry.getIntValue();
            }).collect(Collectors.joining(","));
        } catch (Exception exception) {
            return "";
        }
    }

    public static void makeObsidianPlatform(ServerLevel world) {
        // CraftBukkit start
        ServerLevel.makeObsidianPlatform(world, null);
    }

    public static void makeObsidianPlatform(ServerLevel worldserver, Entity entity) {
        // CraftBukkit end
        BlockPos blockposition = ServerLevel.END_SPAWN_POINT;
        int i = blockposition.getX();
        int j = blockposition.getY() - 2;
        int k = blockposition.getZ();

        // CraftBukkit start
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(worldserver);
        BlockPos.betweenClosed(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2).forEach((blockposition1) -> {
            blockList.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 3);
        });
        BlockPos.betweenClosed(i - 2, j, k - 2, i + 2, j, k + 2).forEach((blockposition1) -> {
            blockList.setBlock(blockposition1, Blocks.OBSIDIAN.defaultBlockState(), 3);
        });
        org.bukkit.World bworld = worldserver.getWorld();
        org.bukkit.event.world.PortalCreateEvent portalEvent = new org.bukkit.event.world.PortalCreateEvent((List<org.bukkit.block.BlockState>) (List) blockList.getList(), bworld, (entity == null) ? null : entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM);

        worldserver.getCraftServer().getPluginManager().callEvent(portalEvent);
        if (!portalEvent.isCancelled()) {
            blockList.updateList();
        }
        // CraftBukkit end
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        org.spigotmc.AsyncCatcher.catchOp("Chunk getEntities call"); // Spigot
        return this.entityLookup; // Paper - rewrite chunk system
    }

    public void addLegacyChunkEntities(Stream<Entity> entities) {
        this.entityLookup.addLegacyChunkEntities(entities.toList()); // Paper - rewrite chunk system
    }

    public void addWorldGenChunkEntities(Stream<Entity> entities) {
        this.entityLookup.addWorldGenChunkEntities(entities.toList()); // Paper - rewrite chunk system
    }

    public void startTickingChunk(LevelChunk chunk) {
        chunk.unpackTicks(this.getLevelData().getGameTime());
    }

    public void onStructureStartsAvailable(ChunkAccess chunk) {
        this.server.execute(() -> {
            this.structureCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        });
    }

    @Override
    public void close() throws IOException {
        super.close();
        //this.entityManager.close(); // Paper - rewrite chunk system
    }

    @Override
    public String gatherChunkSourceStats() {
        String s = this.chunkSource.gatherStats();

        return "Chunks[S] W: " + s + " E: " + this.entityLookup.getDebugInfo(); // Paper - rewrite chunk system
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        // Paper start - rewrite chunk system
        return this.getChunkIfLoadedImmediately(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos)) != null;
        // Paper end - rewrite chunk system
    }

    private boolean isPositionTickingWithEntitiesLoaded(long chunkPos) {
        // Paper start - optimize is ticking ready type functions
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(chunkPos);
        // isTicking implies the chunk is loaded, and the chunk is loaded now implies the entities are loaded
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(io.papermc.paper.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isNaturalSpawningAllowed(BlockPos pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(io.papermc.paper.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isNaturalSpawningAllowed(ChunkPos pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(io.papermc.paper.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    private final class EntityCallbacks implements LevelCallback<Entity> {

        EntityCallbacks() {}

        public void onCreated(Entity entity) {}

        public void onDestroyed(Entity entity) {
            ServerLevel.this.getScoreboard().entityRemoved(entity);
        }

        public void onTickingStart(Entity entity) {
            ServerLevel.this.entityTickList.add(entity);
        }

        public void onTickingEnd(Entity entity) {
            ServerLevel.this.entityTickList.remove(entity);
        }

        public void onTrackingStart(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity register"); // Spigot
            ServerLevel.this.getChunkSource().addEntity(entity);
            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                ServerLevel.this.players.add(entityplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                if (ServerLevel.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";

                    Util.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                ServerLevel.this.navigatingMobs.add(entityinsentient);
            }

            if (entity instanceof EnderDragon) {
                EnderDragon entityenderdragon = (EnderDragon) entity;
                EnderDragonPart[] aentitycomplexpart = entityenderdragon.getSubEntities();
                int i = aentitycomplexpart.length;

                for (int j = 0; j < i; ++j) {
                    EnderDragonPart entitycomplexpart = aentitycomplexpart[j];

                    ServerLevel.this.dragonParts.put(entitycomplexpart.getId(), entitycomplexpart);
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
            entity.valid = true; // CraftBukkit
            // Paper start - Set origin location when the entity is being added to the world
            if (entity.getOriginVector() == null) {
                entity.setOrigin(entity.getBukkitEntity().getLocation());
            }
            // Default to current world if unknown, gross assumption but entities rarely change world
            if (entity.getOriginWorld() == null) {
                entity.setOrigin(entity.getOriginVector().toLocation(getWorld()));
            }
            // Paper end
        }

        public void onTrackingEnd(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity unregister"); // Spigot
            // Spigot start
            if ( entity instanceof Player )
            {
                com.google.common.collect.Streams.stream( ServerLevel.this.getServer().getAllLevels() ).map( ServerLevel::getDataStorage ).forEach( (worldData) ->
                {
                    for (Object o : worldData.cache.values() )
                    {
                        if ( o instanceof MapItemSavedData )
                        {
                            MapItemSavedData map = (MapItemSavedData) o;
                            map.carriedByPlayers.remove( (Player) entity );
                            for ( Iterator<MapItemSavedData.HoldingPlayer> iter = (Iterator<MapItemSavedData.HoldingPlayer>) map.carriedBy.iterator(); iter.hasNext(); )
                            {
                                if ( iter.next().player == entity )
                                {
                                    iter.remove();
                                }
                            }
                        }
                    }
                } );
            }
            // Spigot end
            // Spigot Start
            if (entity.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder && (!(entity instanceof ServerPlayer) || entity.getRemovalReason() != Entity.RemovalReason.KILLED)) { // SPIGOT-6876: closeInventory clears death message
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((org.bukkit.inventory.InventoryHolder) entity.getBukkitEntity()).getInventory().getViewers())) {
                    h.closeInventory();
                }
            }
            // Spigot End
            ServerLevel.this.getChunkSource().removeEntity(entity);
            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                ServerLevel.this.players.remove(entityplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                if (ServerLevel.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";

                    Util.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                ServerLevel.this.navigatingMobs.remove(entityinsentient);
            }

            if (entity instanceof EnderDragon) {
                EnderDragon entityenderdragon = (EnderDragon) entity;
                EnderDragonPart[] aentitycomplexpart = entityenderdragon.getSubEntities();
                int i = aentitycomplexpart.length;

                for (int j = 0; j < i; ++j) {
                    EnderDragonPart entitycomplexpart = aentitycomplexpart[j];

                    ServerLevel.this.dragonParts.remove(entitycomplexpart.getId());
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
            // CraftBukkit start
            entity.valid = false;
            if (!(entity instanceof ServerPlayer)) {
                for (ServerPlayer player : ServerLevel.this.players) {
                    player.getBukkitEntity().onEntityRemove(entity);
                }
            }
            // CraftBukkit end
        }

        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }
}
