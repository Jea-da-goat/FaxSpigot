package net.minecraft.world.level.dimension.end;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockPredicate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class EndDragonFight {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TICKS_BEFORE_DRAGON_RESPAWN = 1200;
    private static final int TIME_BETWEEN_CRYSTAL_SCANS = 100;
    private static final int TIME_BETWEEN_PLAYER_SCANS = 20;
    private static final int ARENA_SIZE_CHUNKS = 8;
    public static final int ARENA_TICKET_LEVEL = 9;
    private static final int GATEWAY_COUNT = 20;
    private static final int GATEWAY_DISTANCE = 96;
    public static final int DRAGON_SPAWN_Y = 128;
    private static final Predicate<Entity> VALID_PLAYER = EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.withinDistance(0.0D, 128.0D, 0.0D, 192.0D));
    private static final Component DEFAULT_BOSS_EVENT_NAME = Component.translatable("entity.minecraft.ender_dragon"); // Paper
    public final ServerBossEvent dragonEvent = (ServerBossEvent)(new ServerBossEvent(DEFAULT_BOSS_EVENT_NAME, BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS)).setPlayBossMusic(true).setCreateWorldFog(true); // Paper
    public final ServerLevel level;
    private final ObjectArrayList<Integer> gateways = new ObjectArrayList<>();
    private final BlockPattern exitPortalPattern;
    private int ticksSinceDragonSeen;
    private int crystalsAlive;
    private int ticksSinceCrystalsScanned;
    private int ticksSinceLastPlayerScan;
    private boolean dragonKilled;
    private boolean previouslyKilled;
    @Nullable
    public UUID dragonUUID;
    private boolean needsStateScanning = true;
    @Nullable
    public BlockPos portalLocation;
    @Nullable
    public DragonRespawnAnimation respawnStage;
    private int respawnTime;
    @Nullable
    private List<EndCrystal> respawnCrystals;

    public EndDragonFight(ServerLevel world, long gatewaysSeed, CompoundTag nbt) {
        // Paper start
        this.needsStateScanning = world.paperConfig().entities.spawning.scanForLegacyEnderDragon;
        if (!this.needsStateScanning) this.dragonKilled = true;
        // Paper end
        this.level = world;
        if (nbt.contains("NeedsStateScanning")) {
            this.needsStateScanning = nbt.getBoolean("NeedsStateScanning");
        }

        if (nbt.contains("DragonKilled", 99)) {
            if (nbt.hasUUID("Dragon")) {
                this.dragonUUID = nbt.getUUID("Dragon");
            }

            this.dragonKilled = nbt.getBoolean("DragonKilled");
            this.previouslyKilled = nbt.getBoolean("PreviouslyKilled");
            if (nbt.getBoolean("IsRespawning")) {
                this.respawnStage = DragonRespawnAnimation.START;
            }

            if (nbt.contains("ExitPortalLocation", 10)) {
                this.portalLocation = NbtUtils.readBlockPos(nbt.getCompound("ExitPortalLocation"));
            }
            // Paper start - Killed statuses should be false for newly created worlds
            // } else {
            //     this.dragonKilled = true;
            //     this.previouslyKilled = true;
            // Paper end
        }

        if (nbt.contains("Gateways", 9)) {
            ListTag listTag = nbt.getList("Gateways", 3);

            for(int i = 0; i < listTag.size(); ++i) {
                this.gateways.add(listTag.getInt(i));
            }
        } else {
            this.gateways.addAll(ContiguousSet.create(Range.closedOpen(0, 20), DiscreteDomain.integers()));
            Util.shuffle(this.gateways, RandomSource.create(gatewaysSeed));
        }

        this.exitPortalPattern = BlockPatternBuilder.start().aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("  ###  ", " #   # ", "#     #", "#  #  #", "#     #", " #   # ", "  ###  ").aisle("       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       ").where('#', BlockInWorld.hasState(BlockPredicate.forBlock(Blocks.BEDROCK))).build();
    }

    public CompoundTag saveData() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putBoolean("NeedsStateScanning", this.needsStateScanning);
        if (this.dragonUUID != null) {
            compoundTag.putUUID("Dragon", this.dragonUUID);
        }

        compoundTag.putBoolean("DragonKilled", this.dragonKilled);
        compoundTag.putBoolean("PreviouslyKilled", this.previouslyKilled);
        if (this.portalLocation != null) {
            compoundTag.put("ExitPortalLocation", NbtUtils.writeBlockPos(this.portalLocation));
        }

        ListTag listTag = new ListTag();

        for(int i : this.gateways) {
            listTag.add(IntTag.valueOf(i));
        }

        compoundTag.put("Gateways", listTag);
        return compoundTag;
    }

    public void tick() {
        this.dragonEvent.setVisible(!this.dragonKilled);
        if (++this.ticksSinceLastPlayerScan >= 20) {
            this.updatePlayers();
            this.ticksSinceLastPlayerScan = 0;
        }

        if (!this.dragonEvent.getPlayers().isEmpty()) {
            this.level.getChunkSource().addRegionTicket(TicketType.DRAGON, new ChunkPos(0, 0), 9, Unit.INSTANCE);
            boolean bl = this.isArenaLoaded();
            if (this.needsStateScanning && bl) {
                this.scanState();
                this.needsStateScanning = false;
            }

            if (this.respawnStage != null) {
                if (this.respawnCrystals == null && bl) {
                    this.respawnStage = null;
                    this.tryRespawn();
                }

                this.respawnStage.tick(this.level, this, this.respawnCrystals, this.respawnTime++, this.portalLocation);
            }

            if (!this.dragonKilled) {
                if ((this.dragonUUID == null || ++this.ticksSinceDragonSeen >= 1200) && bl) {
                    this.findOrCreateDragon();
                    this.ticksSinceDragonSeen = 0;
                }

                if (++this.ticksSinceCrystalsScanned >= 100 && bl) {
                    this.updateCrystalCount();
                    this.ticksSinceCrystalsScanned = 0;
                }
            }
        } else {
            this.level.getChunkSource().removeRegionTicket(TicketType.DRAGON, new ChunkPos(0, 0), 9, Unit.INSTANCE);
        }

    }

    private void scanState() {
        LOGGER.info("Scanning for legacy world dragon fight...");
        boolean bl = this.hasActiveExitPortal();
        if (bl) {
            LOGGER.info("Found that the dragon has been killed in this world already.");
            this.previouslyKilled = true;
        } else {
            LOGGER.info("Found that the dragon has not yet been killed in this world.");
            this.previouslyKilled = false;
            if (this.findExitPortal() == null) {
                this.spawnExitPortal(false);
            }
        }

        List<? extends EnderDragon> list = this.level.getDragons();
        if (list.isEmpty()) {
            this.dragonKilled = true;
        } else {
            EnderDragon enderDragon = list.get(0);
            this.dragonUUID = enderDragon.getUUID();
            LOGGER.info("Found that there's a dragon still alive ({})", (Object)enderDragon);
            this.dragonKilled = false;
            if (!bl && this.level.paperConfig().entities.behavior.shouldRemoveDragon) {
                LOGGER.info("But we didn't have a portal, let's remove it.");
                enderDragon.discard();
                this.dragonUUID = null;
            }
        }

        if (!this.previouslyKilled && this.dragonKilled) {
            this.dragonKilled = false;
        }

    }

    private void findOrCreateDragon() {
        List<? extends EnderDragon> list = this.level.getDragons();
        if (list.isEmpty()) {
            LOGGER.debug("Haven't seen the dragon, respawning it");
            this.createNewDragon();
        } else {
            LOGGER.debug("Haven't seen our dragon, but found another one to use.");
            this.dragonUUID = list.get(0).getUUID();
        }

    }

    public void setRespawnStage(DragonRespawnAnimation spawnState) {
        if (this.respawnStage == null) {
            throw new IllegalStateException("Dragon respawn isn't in progress, can't skip ahead in the animation.");
        } else {
            this.respawnTime = 0;
            if (spawnState == DragonRespawnAnimation.END) {
                this.respawnStage = null;
                this.dragonKilled = false;
                EnderDragon enderDragon = this.createNewDragon();
                if (enderDragon != null) {
                    for(ServerPlayer serverPlayer : this.dragonEvent.getPlayers()) {
                        CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, enderDragon);
                    }
                }
            } else {
                this.respawnStage = spawnState;
            }

        }
    }

    private boolean hasActiveExitPortal() {
        for(int i = -8; i <= 8; ++i) {
            for(int j = -8; j <= 8; ++j) {
                LevelChunk levelChunk = this.level.getChunk(i, j);

                for(BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (blockEntity instanceof TheEndPortalBlockEntity) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    public BlockPattern.BlockPatternMatch findExitPortal() {
        for(int i = -8; i <= 8; ++i) {
            for(int j = -8; j <= 8; ++j) {
                LevelChunk levelChunk = this.level.getChunk(i, j);

                for(BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (blockEntity instanceof TheEndPortalBlockEntity) {
                        BlockPattern.BlockPatternMatch blockPatternMatch = this.exitPortalPattern.find(this.level, blockEntity.getBlockPos());
                        if (blockPatternMatch != null) {
                            BlockPos blockPos = blockPatternMatch.getBlock(3, 3, 3).getPos();
                            if (this.portalLocation == null) {
                                this.portalLocation = blockPos;
                            }

                            return blockPatternMatch;
                        }
                    }
                }
            }
        }

        int k = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.END_PODIUM_LOCATION).getY();

        for(int l = k; l >= this.level.getMinBuildHeight(); --l) {
            BlockPattern.BlockPatternMatch blockPatternMatch2 = this.exitPortalPattern.find(this.level, new BlockPos(EndPodiumFeature.END_PODIUM_LOCATION.getX(), l, EndPodiumFeature.END_PODIUM_LOCATION.getZ()));
            if (blockPatternMatch2 != null) {
                if (this.portalLocation == null) {
                    this.portalLocation = blockPatternMatch2.getBlock(3, 3, 3).getPos();
                }

                return blockPatternMatch2;
            }
        }

        return null;
    }

    private boolean isArenaLoaded() {
        for(int i = -8; i <= 8; ++i) {
            for(int j = 8; j <= 8; ++j) {
                ChunkAccess chunkAccess = this.level.getChunk(i, j, ChunkStatus.FULL, false);
                if (!(chunkAccess instanceof LevelChunk)) {
                    return false;
                }

                ChunkHolder.FullChunkStatus fullChunkStatus = ((LevelChunk)chunkAccess).getFullStatus();
                if (!fullChunkStatus.isOrAfter(ChunkHolder.FullChunkStatus.TICKING)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet();

        for(ServerPlayer serverPlayer : this.level.getPlayers(VALID_PLAYER)) {
            this.dragonEvent.addPlayer(serverPlayer);
            set.add(serverPlayer);
        }

        Set<ServerPlayer> set2 = Sets.newHashSet(this.dragonEvent.getPlayers());
        set2.removeAll(set);

        for(ServerPlayer serverPlayer2 : set2) {
            this.dragonEvent.removePlayer(serverPlayer2);
        }

    }

    private void updateCrystalCount() {
        this.ticksSinceCrystalsScanned = 0;
        this.crystalsAlive = 0;

        for(SpikeFeature.EndSpike endSpike : SpikeFeature.getSpikesForLevel(this.level)) {
            this.crystalsAlive += this.level.getEntitiesOfClass(EndCrystal.class, endSpike.getTopBoundingBox()).size();
        }

        LOGGER.debug("Found {} end crystals still alive", (int)this.crystalsAlive);
    }

    public void setDragonKilled(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(0.0F);
            this.dragonEvent.setVisible(false);
            this.spawnExitPortal(true);
            this.spawnNewGateway();
            // Paper start - DragonEggFormEvent
            BlockPos eggPosition = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.END_PODIUM_LOCATION);
            org.bukkit.craftbukkit.block.CraftBlockState eggState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(this.level, eggPosition);
            eggState.setData(Blocks.DRAGON_EGG.defaultBlockState());
            io.papermc.paper.event.block.DragonEggFormEvent eggEvent = new io.papermc.paper.event.block.DragonEggFormEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this.level, eggPosition), eggState,
                new org.bukkit.craftbukkit.boss.CraftDragonBattle(this));
            // Paper end - DragonEggFormEvent
            if (this.level.paperConfig().entities.behavior.enderDragonsDeathAlwaysPlacesDragonEgg || !this.previouslyKilled) { // Paper - always place dragon egg
                // Paper start - DragonEggFormEvent
                //this.level.setBlockAndUpdate(this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.END_PODIUM_LOCATION), Blocks.DRAGON_EGG.defaultBlockState());
            } else {
                eggEvent.setCancelled(true);
            }
            if (eggEvent.callEvent()) {
                eggEvent.getNewState().update(true);
            }
            // Paper end - DragonEggFormEvent

            this.previouslyKilled = true;
            this.dragonKilled = true;
        }

    }

    private void spawnNewGateway() {
        if (!this.gateways.isEmpty()) {
            int i = this.gateways.remove(this.gateways.size() - 1);
            int j = Mth.floor(96.0D * Math.cos(2.0D * (-Math.PI + 0.15707963267948966D * (double)i)));
            int k = Mth.floor(96.0D * Math.sin(2.0D * (-Math.PI + 0.15707963267948966D * (double)i)));
            this.spawnNewGateway(new BlockPos(j, 75, k));
        }
    }

    private void spawnNewGateway(BlockPos pos) {
        this.level.levelEvent(3000, pos, 0);
        this.level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((registry) -> {
            return registry.getHolder(EndFeatures.END_GATEWAY_DELAYED);
        }).ifPresent((reference) -> {
            reference.value().place(this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), pos);
        });
    }

    public void spawnExitPortal(boolean previouslyKilled) {
        EndPodiumFeature endPodiumFeature = new EndPodiumFeature(previouslyKilled);
        if (this.portalLocation == null) {
            for(this.portalLocation = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.END_PODIUM_LOCATION).below(); this.level.getBlockState(this.portalLocation).is(Blocks.BEDROCK) && this.portalLocation.getY() > this.level.getSeaLevel(); this.portalLocation = this.portalLocation.below()) {
            }
        }

        // Paper start - Prevent "softlocked" exit portal generation
        if (this.portalLocation.getY() <= this.level.getMinBuildHeight()) {
            this.portalLocation = this.portalLocation.atY(this.level.getMinBuildHeight() + 1);
        }
        // Paper end
        endPodiumFeature.place(FeatureConfiguration.NONE, this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), this.portalLocation);
    }

    @Nullable
    private EnderDragon createNewDragon() {
        this.level.getChunkAt(new BlockPos(0, 128, 0));
        EnderDragon enderDragon = EntityType.ENDER_DRAGON.create(this.level);
        if (enderDragon != null) {
            enderDragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            enderDragon.moveTo(0.0D, 128.0D, 0.0D, this.level.random.nextFloat() * 360.0F, 0.0F);
            this.level.addFreshEntity(enderDragon);
            this.dragonUUID = enderDragon.getUUID();
            this.resetSpikeCrystals(); // Paper
        }

        return enderDragon;
    }

    public void updateDragon(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(dragon.getHealth() / dragon.getMaxHealth());
            this.ticksSinceDragonSeen = 0;
            if (dragon.hasCustomName()) {
                this.dragonEvent.setName(dragon.getDisplayName());
                // Paper start - reset to default name
            } else {
                this.dragonEvent.setName(DEFAULT_BOSS_EVENT_NAME);
                // Paper end
            }
        }

    }

    public int getCrystalsAlive() {
        return this.crystalsAlive;
    }

    public void onCrystalDestroyed(EndCrystal enderCrystal, DamageSource source) {
        if (this.respawnStage != null && this.respawnCrystals.contains(enderCrystal)) {
            LOGGER.debug("Aborting respawn sequence");
            this.respawnStage = null;
            this.respawnTime = 0;
            this.resetSpikeCrystals();
            this.spawnExitPortal(true);
        } else {
            this.updateCrystalCount();
            Entity entity = this.level.getEntity(this.dragonUUID);
            if (entity instanceof EnderDragon) {
                ((EnderDragon)entity).onCrystalDestroyed(enderCrystal, enderCrystal.blockPosition(), source);
            }
        }

    }

    public boolean hasPreviouslyKilledDragon() {
        return this.previouslyKilled;
    }

    public void tryRespawn() {
        if (this.dragonKilled && this.respawnStage == null) {
            BlockPos blockPos = this.portalLocation;
            if (blockPos == null) {
                LOGGER.debug("Tried to respawn, but need to find the portal first.");
                BlockPattern.BlockPatternMatch blockPatternMatch = this.findExitPortal();
                if (blockPatternMatch == null) {
                    LOGGER.debug("Couldn't find a portal, so we made one.");
                    this.spawnExitPortal(true);
                } else {
                    LOGGER.debug("Found the exit portal & saved its location for next time.");
                }

                blockPos = this.portalLocation;
            }

            List<EndCrystal> list = Lists.newArrayList();
            BlockPos blockPos2 = blockPos.above(1);

            for(Direction direction : Direction.Plane.HORIZONTAL) {
                List<EndCrystal> list2 = this.level.getEntitiesOfClass(EndCrystal.class, new AABB(blockPos2.relative(direction, 2)));
                if (list2.isEmpty()) {
                    return;
                }

                list.addAll(list2);
            }

            LOGGER.debug("Found all crystals, respawning dragon.");
            this.respawnDragon(list);
        }

    }

    private void respawnDragon(List<EndCrystal> crystals) {
        if (this.dragonKilled && this.respawnStage == null) {
            for(BlockPattern.BlockPatternMatch blockPatternMatch = this.findExitPortal(); blockPatternMatch != null; blockPatternMatch = this.findExitPortal()) {
                for(int i = 0; i < this.exitPortalPattern.getWidth(); ++i) {
                    for(int j = 0; j < this.exitPortalPattern.getHeight(); ++j) {
                        for(int k = 0; k < this.exitPortalPattern.getDepth(); ++k) {
                            BlockInWorld blockInWorld = blockPatternMatch.getBlock(i, j, k);
                            if (blockInWorld.getState().is(Blocks.BEDROCK) || blockInWorld.getState().is(Blocks.END_PORTAL)) {
                                this.level.setBlockAndUpdate(blockInWorld.getPos(), Blocks.END_STONE.defaultBlockState());
                            }
                        }
                    }
                }
            }

            this.respawnStage = DragonRespawnAnimation.START;
            this.respawnTime = 0;
            this.spawnExitPortal(false);
            this.respawnCrystals = crystals;
        }

    }

    public void resetSpikeCrystals() {
        for(SpikeFeature.EndSpike endSpike : SpikeFeature.getSpikesForLevel(this.level)) {
            for(EndCrystal endCrystal : this.level.getEntitiesOfClass(EndCrystal.class, endSpike.getTopBoundingBox())) {
                endCrystal.setInvulnerable(false);
                endCrystal.setBeamTarget((BlockPos)null);
            }
        }

    }
}
