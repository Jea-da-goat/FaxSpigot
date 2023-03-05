package net.minecraft.world.level.block.entity;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SkullBlockEntity extends BlockEntity {

    public static final String TAG_SKULL_OWNER = "SkullOwner";
    public static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    @Nullable
    private static GameProfileCache profileCache;
    @Nullable
    private static MinecraftSessionService sessionService;
    @Nullable
    private static Executor mainThreadExecutor;
    @Nullable
    public GameProfile owner;
    @Nullable
    public ResourceLocation noteBlockSound; // PAIL private->public
    private int animationTickCount;
    private boolean isAnimating;

    public SkullBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.SKULL, pos, state);
    }

    public static void setup(Services apiServices, Executor executor) {
        SkullBlockEntity.profileCache = apiServices.profileCache();
        SkullBlockEntity.sessionService = apiServices.sessionService();
        SkullBlockEntity.mainThreadExecutor = executor;
    }

    public static void clear() {
        SkullBlockEntity.profileCache = null;
        SkullBlockEntity.sessionService = null;
        SkullBlockEntity.mainThreadExecutor = null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.owner != null) {
            CompoundTag nbttagcompound1 = new CompoundTag();

            NbtUtils.writeGameProfile(nbttagcompound1, this.owner);
            nbt.put("SkullOwner", nbttagcompound1);
        }

        if (this.noteBlockSound != null) {
            nbt.putString("note_block_sound", this.noteBlockSound.toString());
        }

    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("SkullOwner", 10)) {
            this.setOwner(NbtUtils.readGameProfile(nbt.getCompound("SkullOwner")));
        } else if (nbt.contains("ExtraType", 8)) {
            String s = nbt.getString("ExtraType");

            if (!StringUtil.isNullOrEmpty(s)) {
                this.setOwner(new GameProfile((UUID) null, s));
            }
        }

        if (nbt.contains("note_block_sound", 8)) {
            this.noteBlockSound = ResourceLocation.tryParse(nbt.getString("note_block_sound"));
        }

    }

    public static void animation(Level world, BlockPos pos, BlockState state, SkullBlockEntity blockEntity) {
        if (world.hasNeighborSignal(pos)) {
            blockEntity.isAnimating = true;
            ++blockEntity.animationTickCount;
        } else {
            blockEntity.isAnimating = false;
        }

    }

    public float getAnimation(float tickDelta) {
        return this.isAnimating ? (float) this.animationTickCount + tickDelta : (float) this.animationTickCount;
    }

    @Nullable
    public GameProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public ResourceLocation getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public void setOwner(@Nullable GameProfile owner) {
        synchronized (this) {
            this.owner = owner;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        SkullBlockEntity.updateGameprofile(this.owner, (gameprofile) -> {
            this.owner = gameprofile;
            this.setChanged();
        });
    }

    public static void updateGameprofile(@Nullable GameProfile owner, Consumer<GameProfile> callback) {
        if (owner != null && !StringUtil.isNullOrEmpty(owner.getName()) && (!owner.isComplete() || !owner.getProperties().containsKey("textures")) && SkullBlockEntity.profileCache != null && SkullBlockEntity.sessionService != null) {
            SkullBlockEntity.profileCache.getAsync(owner.getName(), (optional) -> {
                Util.backgroundExecutor().execute(() -> {
                    Util.ifElse(optional, (gameprofile1) -> {
                        Property property = (Property) Iterables.getFirst(gameprofile1.getProperties().get("textures"), (Object) null);

                        if (property == null) {
                            gameprofile1 = SkullBlockEntity.sessionService.fillProfileProperties(gameprofile1, true);
                        }

                        // CraftBukkit start - decompile error
                        final GameProfile finalgameprofile1 = gameprofile1;
                        SkullBlockEntity.mainThreadExecutor.execute(() -> {
                            SkullBlockEntity.profileCache.add(finalgameprofile1);
                            callback.accept(finalgameprofile1);
                            // CraftBukkit end
                        });
                    }, () -> {
                        SkullBlockEntity.mainThreadExecutor.execute(() -> {
                            callback.accept(owner);
                        });
                    });
                });
            });
        } else {
            callback.accept(owner);
        }
    }
}
