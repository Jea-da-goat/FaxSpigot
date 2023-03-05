package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class IndirectEntityDamageSource extends EntityDamageSource {

    @Nullable
    private final Entity cause;

    public IndirectEntityDamageSource(String name, Entity projectile, @Nullable Entity attacker) {
        super(name, projectile);
        this.cause = attacker;
    }

    @Nullable
    @Override
    public Entity getDirectEntity() {
        return this.entity;
    }

    @Nullable
    @Override
    public Entity getEntity() {
        return this.cause;
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity entity) {
        Component ichatbasecomponent = this.cause == null ? this.entity.getDisplayName() : this.cause.getDisplayName();
        Entity entity1 = this.cause;
        ItemStack itemstack;

        if (entity1 instanceof LivingEntity) {
            LivingEntity entityliving1 = (LivingEntity) entity1;

            itemstack = entityliving1.getMainHandItem();
        } else {
            itemstack = ItemStack.EMPTY;
        }

        ItemStack itemstack1 = itemstack;
        String s = "death.attack." + this.msgId;

        if (!itemstack1.isEmpty() && itemstack1.hasCustomHoverName()) {
            String s1 = s + ".item";

            return Component.translatable(s1, entity.getDisplayName(), ichatbasecomponent, itemstack1.getDisplayName());
        } else {
            return Component.translatable(s, entity.getDisplayName(), ichatbasecomponent);
        }
    }

    // CraftBukkit start
    public Entity getProximateDamageSource() {
        return super.getEntity();
    }
    // CraftBukkit end
}
