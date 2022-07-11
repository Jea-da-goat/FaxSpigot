package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class EntityDamageSource extends DamageSource {
    protected final Entity entity;
    private boolean isThorns;

    public EntityDamageSource(String name, Entity source) {
        super(name);
        this.entity = source;
    }

    public EntityDamageSource setThorns() {
        this.isThorns = true;
        return this;
    }

    public boolean isThorns() {
        return this.isThorns;
    }

    @Override
    public Entity getEntity() {
        return this.entity;
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity entity) {
        Entity var4 = this.entity;
        ItemStack var10000;
        if (var4 instanceof LivingEntity livingEntity) {
            var10000 = livingEntity.getMainHandItem();
        } else {
            var10000 = ItemStack.EMPTY;
        }

        ItemStack itemStack = var10000;
        String string = "death.attack." + this.msgId;
        return !itemStack.isEmpty() && itemStack.hasCustomHoverName() ? Component.translatable(string + ".item", entity.getDisplayName(), this.entity.getDisplayName(), itemStack.getDisplayName()) : Component.translatable(string, entity.getDisplayName(), this.entity.getDisplayName());
    }

    @Override
    public boolean scalesWithDifficulty() {
        return super.scalesWithDifficulty() || this.entity instanceof LivingEntity && !(this.entity instanceof Player); // Paper - fix MC-258535 - respect the scalesWithDifficulty override
    }

    @Nullable
    @Override
    public Vec3 getSourcePosition() {
        return this.entity.position();
    }

    @Override
    public String toString() {
        return "EntityDamageSource (" + this.entity + ")";
    }
}
