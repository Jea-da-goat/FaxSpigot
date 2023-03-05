package net.minecraft.world.item.trading;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe; // CraftBukkit

public class MerchantOffer {

    public ItemStack baseCostA;
    public ItemStack costB;
    public final ItemStack result;
    public int uses;
    public int maxUses;
    public boolean rewardExp;
    public int specialPriceDiff;
    public int demand;
    public float priceMultiplier;
    public int xp;
    // CraftBukkit start
    private CraftMerchantRecipe bukkitHandle;

    public CraftMerchantRecipe asBukkit() {
        return (this.bukkitHandle == null) ? this.bukkitHandle = new CraftMerchantRecipe(this) : this.bukkitHandle;
    }

    public MerchantOffer(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2, int uses, int maxUses, int experience, float priceMultiplier, CraftMerchantRecipe bukkit) {
        this(itemstack, itemstack1, itemstack2, uses, maxUses, experience, priceMultiplier, 0, bukkit);
    }

    public MerchantOffer(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2, int uses, int maxUses, int experience, float priceMultiplier, int demand, CraftMerchantRecipe bukkit) {
        this(itemstack, itemstack1, itemstack2, uses, maxUses, experience, priceMultiplier, demand);
        this.bukkitHandle = bukkit;
    }
    // CraftBukkit end

    public MerchantOffer(CompoundTag nbt) {
        this.rewardExp = true;
        this.xp = 1;
        this.baseCostA = ItemStack.of(nbt.getCompound("buy"));
        this.costB = ItemStack.of(nbt.getCompound("buyB"));
        this.result = ItemStack.of(nbt.getCompound("sell"));
        this.uses = nbt.getInt("uses");
        if (nbt.contains("maxUses", 99)) {
            this.maxUses = nbt.getInt("maxUses");
        } else {
            this.maxUses = 4;
        }

        if (nbt.contains("rewardExp", 1)) {
            this.rewardExp = nbt.getBoolean("rewardExp");
        }

        if (nbt.contains("xp", 3)) {
            this.xp = nbt.getInt("xp");
        }

        if (nbt.contains("priceMultiplier", 5)) {
            this.priceMultiplier = nbt.getFloat("priceMultiplier");
        }

        this.specialPriceDiff = nbt.getInt("specialPrice");
        this.demand = nbt.getInt("demand");
    }

    public MerchantOffer(ItemStack buyItem, ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier) {
        this(buyItem, ItemStack.EMPTY, sellItem, maxUses, merchantExperience, priceMultiplier);
    }

    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier) {
        this(firstBuyItem, secondBuyItem, sellItem, 0, maxUses, merchantExperience, priceMultiplier);
    }

    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier) {
        this(firstBuyItem, secondBuyItem, sellItem, uses, maxUses, merchantExperience, priceMultiplier, 0);
    }

    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier, int demandBonus) {
        this.rewardExp = true;
        this.xp = 1;
        this.baseCostA = firstBuyItem;
        this.costB = secondBuyItem;
        this.result = sellItem;
        this.uses = uses;
        this.maxUses = maxUses;
        this.xp = merchantExperience;
        this.priceMultiplier = priceMultiplier;
        this.demand = demandBonus;
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA;
    }

    public ItemStack getCostA() {
        int i = this.baseCostA.getCount();
        if (i <= 0) return ItemStack.EMPTY; // CraftBukkit - SPIGOT-5476
        ItemStack itemstack = this.baseCostA.copy();
        int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));

        itemstack.setCount(Mth.clamp(i + j + this.specialPriceDiff, (int) 1, this.baseCostA.getItem().getMaxStackSize()));
        return itemstack;
    }

    public ItemStack getCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = this.demand + this.uses - (this.maxUses - this.uses);
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        ++this.uses;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int increment) {
        this.specialPriceDiff += increment;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int specialPrice) {
        this.specialPriceDiff = specialPrice;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public CompoundTag createTag() {
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.put("buy", this.baseCostA.save(new CompoundTag()));
        nbttagcompound.put("sell", this.result.save(new CompoundTag()));
        nbttagcompound.put("buyB", this.costB.save(new CompoundTag()));
        nbttagcompound.putInt("uses", this.uses);
        nbttagcompound.putInt("maxUses", this.maxUses);
        nbttagcompound.putBoolean("rewardExp", this.rewardExp);
        nbttagcompound.putInt("xp", this.xp);
        nbttagcompound.putFloat("priceMultiplier", this.priceMultiplier);
        nbttagcompound.putInt("specialPrice", this.specialPriceDiff);
        nbttagcompound.putInt("demand", this.demand);
        return nbttagcompound;
    }

    public boolean satisfiedBy(ItemStack first, ItemStack second) {
        return this.isRequiredItem(first, this.getCostA()) && first.getCount() >= this.getCostA().getCount() && this.isRequiredItem(second, this.costB) && second.getCount() >= this.costB.getCount();
    }

    private boolean isRequiredItem(ItemStack given, ItemStack sample) {
        if (sample.isEmpty() && given.isEmpty()) {
            return true;
        } else {
            ItemStack itemstack2 = given.copy();

            if (itemstack2.getItem().canBeDepleted()) {
                itemstack2.setDamageValue(itemstack2.getDamageValue());
            }

            return ItemStack.isSame(itemstack2, sample) && (!sample.hasTag() || itemstack2.hasTag() && NbtUtils.compareNbt(sample.getTag(), itemstack2.getTag(), false));
        }
    }

    public boolean take(ItemStack firstBuyStack, ItemStack secondBuyStack) {
        if (!this.satisfiedBy(firstBuyStack, secondBuyStack)) {
            return false;
        } else {
            // CraftBukkit start
            if (!this.getCostA().isEmpty()) {
                firstBuyStack.shrink(this.getCostA().getCount());
            }
            // CraftBukkit end
            if (!this.getCostB().isEmpty()) {
                secondBuyStack.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }
}
