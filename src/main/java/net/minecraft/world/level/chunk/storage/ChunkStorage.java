package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
// CraftBukkit start
import java.util.concurrent.ExecutionException;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable {

    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    private final IOWorker worker;
    protected final DataFixer fixerUpper;
    @Nullable
    private volatile LegacyStructureDataHandler legacyStructureHandler;

    public ChunkStorage(Path directory, DataFixer dataFixer, boolean dsync) {
        this.fixerUpper = dataFixer;
        this.worker = new IOWorker(directory, dsync, "chunk");
    }

    public boolean isOldChunkAround(ChunkPos chunkPos, int checkRadius) {
        return this.worker.isOldChunkAround(chunkPos, checkRadius);
    }

    // CraftBukkit start
    private boolean check(ServerChunkCache cps, int x, int z) {
        ChunkPos pos = new ChunkPos(x, z);
        if (cps != null) {
            com.google.common.base.Preconditions.checkState(org.bukkit.Bukkit.isPrimaryThread(), "primary thread");
            if (cps.hasChunk(x, z)) {
                return true;
            }
        }

        CompoundTag nbt;
        try {
            nbt = this.read(pos).get().orElse(null);
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        if (nbt != null) {
            CompoundTag level = nbt.getCompound("Level");
            if (level.getBoolean("TerrainPopulated")) {
                return true;
            }

            ChunkStatus status = ChunkStatus.byName(level.getString("Status"));
            if (status != null && status.isOrAfter(ChunkStatus.FEATURES)) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag upgradeChunkTag(ResourceKey<LevelStem> resourcekey, Supplier<DimensionDataStorage> supplier, CompoundTag nbttagcompound, Optional<ResourceKey<Codec<? extends ChunkGenerator>>> optional, ChunkPos pos, @Nullable LevelAccessor generatoraccess) {
        // CraftBukkit end
        int i = ChunkStorage.getVersion(nbttagcompound);

        // CraftBukkit start
        if (false && i < 1466) { // Paper - no longer needed, data converter system handles it now
            CompoundTag level = nbttagcompound.getCompound("Level");
            if (level.getBoolean("TerrainPopulated") && !level.getBoolean("LightPopulated")) {
                ServerChunkCache cps = (generatoraccess == null) ? null : ((ServerLevel) generatoraccess).getChunkSource();
                if (this.check(cps, pos.x - 1, pos.z) && this.check(cps, pos.x - 1, pos.z - 1) && this.check(cps, pos.x, pos.z - 1)) {
                    level.putBoolean("LightPopulated", true);
                }
            }
        }
        // CraftBukkit end

        if (i < 1493) {
            ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, nbttagcompound, i, 1493); // Paper - replace chunk converter
            if (nbttagcompound.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                LegacyStructureDataHandler persistentstructurelegacy = this.getLegacyStructureHandler(resourcekey, supplier);

                nbttagcompound = persistentstructurelegacy.updateFromLegacy(nbttagcompound);
            }
        }

        // Spigot start - SPIGOT-6806: Quick and dirty way to prevent below zero generation in old chunks, by setting the status to heightmap instead of empty
        boolean stopBelowZero = false;
        boolean belowZeroGenerationInExistingChunks = (generatoraccess != null) ? ((ServerLevel) generatoraccess).spigotConfig.belowZeroGenerationInExistingChunks : org.spigotmc.SpigotConfig.belowZeroGenerationInExistingChunks;

        if (i <= 2730 && !belowZeroGenerationInExistingChunks) {
            stopBelowZero = ChunkStatus.FULL.getName().equals(nbttagcompound.getCompound("Level").getString("Status"));
        }
        // Spigot end

        ChunkStorage.injectDatafixingContext(nbttagcompound, resourcekey, optional);
        nbttagcompound = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, nbttagcompound, Math.max(1493, i), SharedConstants.getCurrentVersion().getWorldVersion()); // Paper - replace chunk converter
        if (i < SharedConstants.getCurrentVersion().getWorldVersion()) {
            nbttagcompound.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        }

        // Spigot start
        if (stopBelowZero) {
            nbttagcompound.putString("Status", ChunkStatus.HEIGHTMAPS.getName());
        }
        // Spigot end

        nbttagcompound.remove("__context");
        return nbttagcompound;
    }

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<LevelStem> worldKey, Supplier<DimensionDataStorage> stateManagerGetter) { // CraftBukkit
        LegacyStructureDataHandler persistentstructurelegacy = this.legacyStructureHandler;

        if (persistentstructurelegacy == null) {
            synchronized (this) {
                persistentstructurelegacy = this.legacyStructureHandler;
                if (persistentstructurelegacy == null) {
                    this.legacyStructureHandler = persistentstructurelegacy = LegacyStructureDataHandler.getLegacyStructureHandler(worldKey, (DimensionDataStorage) stateManagerGetter.get());
                }
            }
        }

        return persistentstructurelegacy;
    }

    public static void injectDatafixingContext(CompoundTag nbt, ResourceKey<LevelStem> worldKey, Optional<ResourceKey<Codec<? extends ChunkGenerator>>> generatorCodecKey) { // CraftBukkit
        CompoundTag nbttagcompound1 = new CompoundTag();

        nbttagcompound1.putString("dimension", worldKey.location().toString());
        generatorCodecKey.ifPresent((resourcekey1) -> {
            nbttagcompound1.putString("generator", resourcekey1.location().toString());
        });
        nbt.put("__context", nbttagcompound1);
    }

    public static int getVersion(CompoundTag nbt) {
        return nbt.contains("DataVersion", 99) ? nbt.getInt("DataVersion") : -1;
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        return this.worker.loadAsync(chunkPos);
    }

    public void write(ChunkPos chunkPos, CompoundTag nbt) {
        this.worker.store(chunkPos, nbt);
        if (this.legacyStructureHandler != null) {
            this.legacyStructureHandler.removeIndex(chunkPos.toLong());
        }

    }

    public void flushWorker() {
        this.worker.synchronize(true).join();
    }

    public void close() throws IOException {
        this.worker.close();
    }

    public ChunkScanAccess chunkScanner() {
        return this.worker;
    }
}