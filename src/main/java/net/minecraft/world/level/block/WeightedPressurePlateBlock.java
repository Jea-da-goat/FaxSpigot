package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;
    private final SoundEvent soundOff;
    private final SoundEvent soundOn;

    protected WeightedPressurePlateBlock(int weight, BlockBehaviour.Properties settings, SoundEvent depressSound, SoundEvent pressSound) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(WeightedPressurePlateBlock.POWER, 0));
        this.maxWeight = weight;
        this.soundOff = depressSound;
        this.soundOn = pressSound;
    }

    @Override
    protected int getSignalStrength(Level world, BlockPos pos) {
        // CraftBukkit start
        // int i = Math.min(world.getEntitiesOfClass(Entity.class, BlockPressurePlateWeighted.TOUCH_AABB.move(blockposition)).size(), this.maxWeight);
        int i = 0;
        java.util.Iterator iterator = world.getEntitiesOfClass(Entity.class, WeightedPressurePlateBlock.TOUCH_AABB.move(pos)).iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            org.bukkit.event.Cancellable cancellable;

            if (entity instanceof Player) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                world.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            // We only want to block turning the plate on if all events are cancelled
            if (!cancellable.isCancelled()) {
                i++;
            }
        }

        i = Math.min(i, this.maxWeight);
        // CraftBukkit end

        if (i > 0) {
            float f = (float) Math.min(this.maxWeight, i) / (float) this.maxWeight;

            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    @Override
    protected void playOnSound(LevelAccessor world, BlockPos pos) {
        world.playSound((Player) null, pos, this.soundOn, SoundSource.BLOCKS);
    }

    @Override
    protected void playOffSound(LevelAccessor world, BlockPos pos) {
        world.playSound((Player) null, pos, this.soundOff, SoundSource.BLOCKS);
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return (Integer) state.getValue(WeightedPressurePlateBlock.POWER);
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int rsOut) {
        return (BlockState) state.setValue(WeightedPressurePlateBlock.POWER, rsOut);
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WeightedPressurePlateBlock.POWER);
    }
}
