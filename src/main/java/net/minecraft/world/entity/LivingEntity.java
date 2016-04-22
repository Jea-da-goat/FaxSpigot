package net.minecraft.world.entity;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.google.common.base.Function;
import org.bukkit.Location;
import org.bukkit.craftbukkit.attribute.CraftAttributeMap;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ArrowBodyCountChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
// CraftBukkit end

import co.aikar.timings.MinecraftTimings; // Paper

public abstract class LivingEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final UUID SPEED_MODIFIER_SOUL_SPEED_UUID = UUID.fromString("87f46a96-686f-4796-b035-22e16ee9e038");
    private static final UUID SPEED_MODIFIER_POWDER_SNOW_UUID = UUID.fromString("1eaf83ff-7207-4596-b37a-d7a07b3ec4ce");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(LivingEntity.SPEED_MODIFIER_SPRINTING_UUID, "Sprinting speed boost", 0.30000001192092896D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    public static final int HAND_SLOTS = 2;
    public static final int ARMOR_SLOTS = 4;
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int SWING_DURATION = 6;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003D;
    public static final double DEFAULT_BASE_GRAVITY = 0.08D;
    public static final int DEATH_DURATION = 20;
    private static final int WAIT_TICKS_BEFORE_ITEM_USE_EFFECTS = 7;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final int USE_ITEM_INTERVAL = 4;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0D;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    protected static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_EFFECT_COLOR_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    protected static final float DEFAULT_EYE_HEIGHT = 1.74F;
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    private final AttributeMap attributes;
    public CombatTracker combatTracker = new CombatTracker(this);
    public final Map<MobEffect, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks;
    private final NonNullList<ItemStack> lastArmorItemStacks;
    public boolean swinging;
    private boolean discardFriction;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public float hurtDir;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public float animationSpeedOld;
    public float animationSpeed;
    public float animationPosition;
    public int invulnerableDuration;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public float flyingSpeed;
    @Nullable
    public net.minecraft.world.entity.player.Player lastHurtByPlayer;
    public int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    protected int deathScore;
    public float lastHurt;
    public boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lyHeadRot;
    protected int lerpHeadSteps;
    public boolean effectsDirty;
    @Nullable
    public LivingEntity lastHurtByMob;
    public int lastHurtByMobTimestamp;
    private LivingEntity lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem;
    protected int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos;
    @Nullable
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    private boolean skipDropExperience;
    // CraftBukkit start
    public int expToDrop;
    public boolean forceDrops;
    public ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>();
    public final org.bukkit.craftbukkit.attribute.CraftAttributeMap craftAttributes;
    public boolean collides = true;
    public Set<UUID> collidableExemptions = new HashSet<>();
    public boolean bukkitPickUpLoot;
    public org.bukkit.craftbukkit.entity.CraftLivingEntity getBukkitLivingEntity() { return (org.bukkit.craftbukkit.entity.CraftLivingEntity) super.getBukkitEntity(); } // Paper

    @Override
    public float getBukkitYaw() {
        return this.getYHeadRot();
    }
    // CraftBukkit end
    // Spigot start
    public void inactiveTick()
    {
        super.inactiveTick();
        ++this.noActionTime; // Above all the floats
    }
    // Spigot end

    protected LivingEntity(EntityType<? extends LivingEntity> type, Level world) {
        super(type, world);
        this.lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
        this.lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
        this.discardFriction = false;
        this.invulnerableDuration = 20;
        this.flyingSpeed = 0.02F;
        this.effectsDirty = true;
        this.useItem = ItemStack.EMPTY;
        this.lastClimbablePos = Optional.empty();
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(type));
        this.craftAttributes = new CraftAttributeMap(this.attributes); // CraftBukkit
        // CraftBukkit - setHealth(getMaxHealth()) inlined and simplified to skip the instanceof check for EntityPlayer, as getBukkitEntity() is not initialized in constructor
        this.entityData.set(LivingEntity.DATA_HEALTH_ID, (float) this.getAttribute(Attributes.MAX_HEALTH).getValue());
        this.blocksBuilding = true;
        this.rotA = (float) ((Math.random() + 1.0D) * 0.009999999776482582D);
        this.reapplyPosition();
        this.timeOffs = (float) Math.random() * 12398.0F;
        this.setYRot((float) (Math.random() * 6.2831854820251465D));
        this.yHeadRot = this.getYRot();
        this.maxUpStep = 0.6F;
        NbtOps dynamicopsnbt = NbtOps.INSTANCE;

        this.brain = this.makeBrain(new Dynamic(dynamicopsnbt, (Tag) dynamicopsnbt.createMap((Map) ImmutableMap.of(dynamicopsnbt.createString("memories"), (Tag) dynamicopsnbt.emptyMap()))));
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    @Override
    public void kill() {
        this.hurt(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> type) {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) 0);
        this.entityData.define(LivingEntity.DATA_EFFECT_COLOR_ID, 0);
        this.entityData.define(LivingEntity.DATA_EFFECT_AMBIENCE_ID, false);
        this.entityData.define(LivingEntity.DATA_ARROW_COUNT_ID, 0);
        this.entityData.define(LivingEntity.DATA_STINGER_COUNT_ID, 0);
        this.entityData.define(LivingEntity.DATA_HEALTH_ID, 1.0F);
        this.entityData.define(LivingEntity.SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return AttributeSupplier.builder().add(Attributes.MAX_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE).add(Attributes.MOVEMENT_SPEED).add(Attributes.ARMOR).add(Attributes.ARMOR_TOUGHNESS);
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (!this.level.isClientSide && onGround && this.fallDistance > 0.0F) {
            this.removeSoulSpeed();
            this.tryAddSoulSpeed();
        }

        if (!this.level.isClientSide && this.fallDistance > 3.0F && onGround) {
            float f = (float) Mth.ceil(this.fallDistance - 3.0F);

            if (!state.isAir()) {
                double d1 = Math.min((double) (0.2F + f / 15.0F), 2.5D);
                int i = (int) (150.0D * d1);

                // CraftBukkit start - visiblity api
                if (this instanceof ServerPlayer) {
                    ((ServerLevel) this.level).sendParticles((ServerPlayer) this, new BlockParticleOption(ParticleTypes.BLOCK, state), this.getX(), this.getY(), this.getZ(), i, 0.0D, 0.0D, 0.0D, 0.15000000596046448D, false);
                } else {
                    ((ServerLevel) this.level).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), this.getX(), this.getY(), this.getZ(), i, 0.0D, 0.0D, 0.0D, 0.15000000596046448D);
                }
                // CraftBukkit end
            }
        }

        super.checkFallDamage(heightDifference, onGround, state, landedPosition);
    }

    public boolean canBreatheUnderwater() {
        return this.getMobType() == MobType.UNDEAD;
    }

    public float getSwimAmount(float tickDelta) {
        return Mth.lerp(tickDelta, this.swimAmountO, this.swimAmount);
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.canSpawnSoulSpeedParticle()) {
            this.spawnSoulSpeedParticle();
        }

        super.baseTick();
        this.level.getProfiler().push("livingEntityBaseTick");
        if (this.fireImmune() || this.level.isClientSide) {
            this.clearFire();
        }

        if (this.isAlive()) {
            boolean flag = this instanceof net.minecraft.world.entity.player.Player;

            if (!this.level.isClientSide) {
                if (this.isInWall()) {
                    this.hurt(DamageSource.IN_WALL, 1.0F);
                } else if (flag && !this.level.getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d0 = this.level.getWorldBorder().getDistanceToBorder(this) + this.level.getWorldBorder().getDamageSafeZone();

                    if (d0 < 0.0D) {
                        double d1 = this.level.getWorldBorder().getDamagePerBlock();

                        if (d1 > 0.0D) {
                            this.hurt(DamageSource.IN_WALL, (float) Math.max(1, Mth.floor(-d0 * d1)));
                        }
                    }
                }
            }

            if (this.isEyeInFluid(FluidTags.WATER) && !this.level.getBlockState(new BlockPos(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                boolean flag1 = !this.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(this) && (!flag || !((net.minecraft.world.entity.player.Player) this).getAbilities().invulnerable);

                if (flag1) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -20) {
                        this.setAirSupply(0);
                        Vec3 vec3d = this.getDeltaMovement();

                        for (int i = 0; i < 8; ++i) {
                            double d2 = this.random.nextDouble() - this.random.nextDouble();
                            double d3 = this.random.nextDouble() - this.random.nextDouble();
                            double d4 = this.random.nextDouble() - this.random.nextDouble();

                            this.level.addParticle(ParticleTypes.BUBBLE, this.getX() + d2, this.getY() + d3, this.getZ() + d4, vec3d.x, vec3d.y, vec3d.z);
                        }

                        this.hurt(DamageSource.DROWN, 2.0F);
                    }
                }

                if (!this.level.isClientSide && this.isPassenger() && this.getVehicle() != null && !this.getVehicle().rideableUnderWater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (!this.level.isClientSide) {
                BlockPos blockposition = this.blockPosition();

                if (!Objects.equal(this.lastPos, blockposition)) {
                    this.lastPos = blockposition;
                    this.onChangedBlock(blockposition);
                }
            }
        }

        if (this.isAlive() && (this.isInWaterRainOrBubble() || this.isInPowderSnow)) {
            this.extinguishFire();
        }

        if (this.hurtTime > 0) {
            --this.hurtTime;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            --this.invulnerableTime;
        }

        if (this.isDeadOrDying() && this.level.shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            --this.lastHurtByPlayerTime;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob((LivingEntity) null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob((LivingEntity) null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.level.getProfiler().pop();
    }

    public boolean canSpawnSoulSpeedParticle() {
        return this.tickCount % 5 == 0 && this.getDeltaMovement().x != 0.0D && this.getDeltaMovement().z != 0.0D && !this.isSpectator() && EnchantmentHelper.hasSoulSpeed(this) && this.onSoulSpeedBlock();
    }

    protected void spawnSoulSpeedParticle() {
        Vec3 vec3d = this.getDeltaMovement();

        this.level.addParticle(ParticleTypes.SOUL, this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(), this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(), vec3d.x * -0.2D, 0.1D, vec3d.z * -0.2D);
        float f = this.random.nextFloat() * 0.4F + this.random.nextFloat() > 0.9F ? 0.6F : 0.0F;

        this.playSound(SoundEvents.SOUL_ESCAPE, f, 0.6F + this.random.nextFloat() * 0.4F);
    }

    protected boolean onSoulSpeedBlock() {
        return this.level.getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).is(BlockTags.SOUL_SPEED_BLOCKS);
    }

    @Override
    protected float getBlockSpeedFactor() {
        return this.onSoulSpeedBlock() && EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED, this) > 0 ? 1.0F : super.getBlockSpeedFactor();
    }

    protected boolean shouldRemoveSoulSpeed(BlockState landingState) {
        return !landingState.isAir() || this.isFallFlying();
    }

    protected void removeSoulSpeed() {
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributemodifiable != null) {
            if (attributemodifiable.getModifier(LivingEntity.SPEED_MODIFIER_SOUL_SPEED_UUID) != null) {
                attributemodifiable.removeModifier(LivingEntity.SPEED_MODIFIER_SOUL_SPEED_UUID);
            }

        }
    }

    protected void tryAddSoulSpeed() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED, this);

            if (i > 0 && this.onSoulSpeedBlock()) {
                AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

                if (attributemodifiable == null) {
                    return;
                }

                attributemodifiable.addTransientModifier(new AttributeModifier(LivingEntity.SPEED_MODIFIER_SOUL_SPEED_UUID, "Soul speed boost", (double) (0.03F * (1.0F + (float) i * 0.35F)), AttributeModifier.Operation.ADDITION));
                if (this.getRandom().nextFloat() < 0.04F) {
                    ItemStack itemstack = this.getItemBySlot(EquipmentSlot.FEET);

                    itemstack.hurtAndBreak(1, this, (entityliving) -> {
                        entityliving.broadcastBreakEvent(EquipmentSlot.FEET);
                    });
                }
            }
        }

    }

    protected void removeFrost() {
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributemodifiable != null) {
            if (attributemodifiable.getModifier(LivingEntity.SPEED_MODIFIER_POWDER_SNOW_UUID) != null) {
                attributemodifiable.removeModifier(LivingEntity.SPEED_MODIFIER_POWDER_SNOW_UUID);
            }

        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = this.getTicksFrozen();

            if (i > 0) {
                AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

                if (attributemodifiable == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();

                attributemodifiable.addTransientModifier(new AttributeModifier(LivingEntity.SPEED_MODIFIER_POWDER_SNOW_UUID, "Powder snow slow", (double) f, AttributeModifier.Operation.ADDITION));
            }
        }

    }

    protected void onChangedBlock(BlockPos pos) {
        int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.FROST_WALKER, this);

        if (i > 0) {
            FrostWalkerEnchantment.onEntityMoved(this, this.level, pos, i);
        }

        if (this.shouldRemoveSoulSpeed(this.getBlockStateOnLegacy())) {
            this.removeSoulSpeed();
        }

        this.tryAddSoulSpeed();
    }

    public boolean isBaby() {
        return false;
    }

    public float getScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    protected boolean isAffectedByFluids() {
        return true;
    }

    @Override
    public boolean rideableUnderWater() {
        return false;
    }

    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 20 && !this.level.isClientSide() && !this.isRemoved()) {
            this.level.broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }

    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    protected int decreaseAirSupply(int air) {
        int j = EnchantmentHelper.getRespiration(this);

        return j > 0 && this.random.nextInt(j + 1) > 0 ? air : air - 1;
    }

    protected int increaseAirSupply(int air) {
        return Math.min(air + 4, this.getMaxAirSupply());
    }

    public int getExperienceReward() {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    public RandomSource getRandom() {
        return this.random;
    }

    @Nullable
    public LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable net.minecraft.world.entity.player.Player attacking) {
        this.lastHurtByPlayer = attacking;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    public void setLastHurtByMob(@Nullable LivingEntity attacker) {
        this.lastHurtByMob = attacker;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity target) {
        if (target instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity) target;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int despawnCounter) {
        this.noActionTime = despawnCounter;
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(boolean noDrag) {
        this.discardFriction = noDrag;
    }

    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return true;
    }

    public void onEquipItem(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack) {
        // CraftBukkit start
        this.onEquipItem(slot, oldStack, newStack, false);
    }

    public void onEquipItem(EquipmentSlot enumitemslot, ItemStack itemstack, ItemStack itemstack1, boolean silent) {
        // CraftBukkit end
        boolean flag = itemstack1.isEmpty() && itemstack.isEmpty();

        if (!flag && !ItemStack.isSame(itemstack, itemstack1) && !this.firstTick) {
            if (enumitemslot.getType() == EquipmentSlot.Type.ARMOR && !silent) { // CraftBukkit
                this.playEquipSound(itemstack1);
            }

            if (this.doesEmitEquipEvent(enumitemslot)) {
                this.gameEvent(GameEvent.EQUIP);
            }

        }
    }

    protected void playEquipSound(ItemStack stack) {
        if (!stack.isEmpty() && !this.isSpectator()) {
            SoundEvent soundeffect = stack.getEquipSound();

            if (soundeffect != null) {
                this.playSound(soundeffect, 1.0F, 1.0F);
            }

        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putFloat("Health", this.getHealth());
        nbt.putShort("HurtTime", (short) this.hurtTime);
        nbt.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        nbt.putShort("DeathTime", (short) this.deathTime);
        nbt.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        nbt.put("Attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag nbttaglist = new ListTag();
            Iterator iterator = this.activeEffects.values().iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                nbttaglist.add(mobeffect.save(new CompoundTag()));
            }

            nbt.put("ActiveEffects", nbttaglist);
        }

        nbt.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent((blockposition) -> {
            nbt.putInt("SleepingX", blockposition.getX());
            nbt.putInt("SleepingY", blockposition.getY());
            nbt.putInt("SleepingZ", blockposition.getZ());
        });
        DataResult<Tag> dataresult = this.brain.serializeStart(NbtOps.INSTANCE);
        Logger logger = LivingEntity.LOGGER;

        java.util.Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("Brain", nbtbase);
        });
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        // Paper start - jvm keeps optimizing the setter
        float absorptionAmount = nbt.getFloat("AbsorptionAmount");
        if (Float.isNaN(absorptionAmount)) {
            absorptionAmount = 0;
        }
        this.setAbsorptionAmount(absorptionAmount);
        // Paper end
        if (nbt.contains("Attributes", 9) && this.level != null && !this.level.isClientSide) {
            this.getAttributes().load(nbt.getList("Attributes", 10));
        }

        if (nbt.contains("ActiveEffects", 9)) {
            ListTag nbttaglist = nbt.getList("ActiveEffects", 10);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);
                MobEffectInstance mobeffect = MobEffectInstance.load(nbttagcompound1);

                if (mobeffect != null) {
                    this.activeEffects.put(mobeffect.getEffect(), mobeffect);
                }
            }
        }

        // CraftBukkit start
        if (nbt.contains("Bukkit.MaxHealth")) {
            Tag nbtbase = nbt.get("Bukkit.MaxHealth");
            if (nbtbase.getId() == 5) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(((FloatTag) nbtbase).getAsDouble());
            } else if (nbtbase.getId() == 3) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(((IntTag) nbtbase).getAsDouble());
            }
        }
        // CraftBukkit end

        if (nbt.contains("Health", 99)) {
            this.setHealth(nbt.getFloat("Health"));
        }

        this.hurtTime = nbt.getShort("HurtTime");
        this.deathTime = nbt.getShort("DeathTime");
        this.lastHurtByMobTimestamp = nbt.getInt("HurtByTimestamp");
        if (nbt.contains("Team", 8)) {
            String s = nbt.getString("Team");
            PlayerTeam scoreboardteam = this.level.getScoreboard().getPlayerTeam(s);
            if (!level.paperConfig().scoreboards.allowNonPlayerEntitiesOnScoreboards && !(this instanceof net.minecraft.world.entity.player.Player)) { scoreboardteam = null; } // Paper
            boolean flag = scoreboardteam != null && this.level.getScoreboard().addPlayerToTeam(this.getStringUUID(), scoreboardteam);

            if (!flag) {
                LivingEntity.LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", s);
            }
        }

        if (nbt.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (nbt.contains("SleepingX", 99) && nbt.contains("SleepingY", 99) && nbt.contains("SleepingZ", 99)) {
            BlockPos blockposition = new BlockPos(nbt.getInt("SleepingX"), nbt.getInt("SleepingY"), nbt.getInt("SleepingZ"));

            this.setSleepingPos(blockposition);
            this.entityData.set(LivingEntity.DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockposition);
            }
        }

        if (nbt.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic(NbtOps.INSTANCE, nbt.get("Brain")));
        }

    }

    // CraftBukkit start
    private boolean isTickingEffects = false;
    private List<ProcessableEffect> effectsToProcess = Lists.newArrayList();

    private static class ProcessableEffect {

        private MobEffect type;
        private MobEffectInstance effect;
        private final EntityPotionEffectEvent.Cause cause;

        private ProcessableEffect(MobEffectInstance effect, EntityPotionEffectEvent.Cause cause) {
            this.effect = effect;
            this.cause = cause;
        }

        private ProcessableEffect(MobEffect type, EntityPotionEffectEvent.Cause cause) {
            this.type = type;
            this.cause = cause;
        }
    }
    // CraftBukkit end

    protected void tickEffects() {
        Iterator iterator = this.activeEffects.keySet().iterator();

        this.isTickingEffects = true; // CraftBukkit
        try {
            while (iterator.hasNext()) {
                MobEffect mobeffectlist = (MobEffect) iterator.next();
                MobEffectInstance mobeffect = (MobEffectInstance) this.activeEffects.get(mobeffectlist);

                if (!mobeffect.tick(this, () -> {
                    this.onEffectUpdated(mobeffect, true, (Entity) null);
                })) {
                    if (!this.level.isClientSide) {
                        // CraftBukkit start
                        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobeffect, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.EXPIRATION);
                        if (event.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end
                        iterator.remove();
                        this.onEffectRemoved(mobeffect);
                    }
                } else if (mobeffect.getDuration() % 600 == 0) {
                    this.onEffectUpdated(mobeffect, false, (Entity) null);
                }
            }
        } catch (ConcurrentModificationException concurrentmodificationexception) {
            ;
        }
        // CraftBukkit start
        this.isTickingEffects = false;
        for (ProcessableEffect e : this.effectsToProcess) {
            if (e.effect != null) {
                this.addEffect(e.effect, e.cause);
            } else {
                this.removeEffect(e.type, e.cause);
            }
        }
        this.effectsToProcess.clear();
        // CraftBukkit end

        if (this.effectsDirty) {
            if (!this.level.isClientSide) {
                this.updateInvisibilityStatus();
                this.updateGlowingStatus();
            }

            this.effectsDirty = false;
        }

        int i = (Integer) this.entityData.get(LivingEntity.DATA_EFFECT_COLOR_ID);
        boolean flag = (Boolean) this.entityData.get(LivingEntity.DATA_EFFECT_AMBIENCE_ID);

        if (i > 0) {
            boolean flag1;

            if (this.isInvisible()) {
                flag1 = this.random.nextInt(15) == 0;
            } else {
                flag1 = this.random.nextBoolean();
            }

            if (flag) {
                flag1 &= this.random.nextInt(5) == 0;
            }

            if (flag1 && i > 0) {
                double d0 = (double) (i >> 16 & 255) / 255.0D;
                double d1 = (double) (i >> 8 & 255) / 255.0D;
                double d2 = (double) (i >> 0 & 255) / 255.0D;

                this.level.addParticle(flag ? ParticleTypes.AMBIENT_ENTITY_EFFECT : ParticleTypes.ENTITY_EFFECT, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), d0, d1, d2);
            }
        }

    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            Collection<MobEffectInstance> collection = this.activeEffects.values();

            this.entityData.set(LivingEntity.DATA_EFFECT_AMBIENCE_ID, LivingEntity.areAllEffectsAmbient(collection));
            this.entityData.set(LivingEntity.DATA_EFFECT_COLOR_ID, PotionUtils.getColor(collection));
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
        }

    }

    private void updateGlowingStatus() {
        boolean flag = this.isCurrentlyGlowing();

        if (this.getSharedFlag(6) != flag) {
            this.setSharedFlag(6, flag);
        }

    }

    public double getVisibilityPercent(@Nullable Entity entity) {
        double d0 = 1.0D;

        if (this.isDiscrete()) {
            d0 *= 0.8D;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();

            if (f < 0.1F) {
                f = 0.1F;
            }

            d0 *= 0.7D * (double) f;
        }

        if (entity != null) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
            EntityType<?> entitytypes = entity.getType();

            if (entitytypes == EntityType.SKELETON && itemstack.is(Items.SKELETON_SKULL) || entitytypes == EntityType.ZOMBIE && itemstack.is(Items.ZOMBIE_HEAD) || entitytypes == EntityType.PIGLIN && itemstack.is(Items.PIGLIN_HEAD) || entitytypes == EntityType.PIGLIN_BRUTE && itemstack.is(Items.PIGLIN_HEAD) || entitytypes == EntityType.CREEPER && itemstack.is(Items.CREEPER_HEAD)) {
                d0 *= 0.5D;
            }
        }

        return d0;
    }

    public boolean canAttack(LivingEntity target) {
        return target instanceof net.minecraft.world.entity.player.Player && this.level.getDifficulty() == Difficulty.PEACEFUL ? false : target.canBeSeenAsEnemy();
    }

    public boolean canAttack(LivingEntity entity, TargetingConditions predicate) {
        return predicate.test(this, entity);
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> effects) {
        Iterator iterator = effects.iterator();

        MobEffectInstance mobeffect;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            mobeffect = (MobEffectInstance) iterator.next();
        } while (!mobeffect.isVisible() || mobeffect.isAmbient());

        return false;
    }

    protected void removeEffectParticles() {
        this.entityData.set(LivingEntity.DATA_EFFECT_AMBIENCE_ID, false);
        this.entityData.set(LivingEntity.DATA_EFFECT_COLOR_ID, 0);
    }

    // CraftBukkit start
    public boolean removeAllEffects() {
        return this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeAllEffects(EntityPotionEffectEvent.Cause cause) {
        // CraftBukkit end
        if (this.level.isClientSide) {
            return false;
        } else {
            Iterator<MobEffectInstance> iterator = this.activeEffects.values().iterator();

            boolean flag;

            for (flag = false; iterator.hasNext(); flag = true) {
                // CraftBukkit start
                MobEffectInstance effect = (MobEffectInstance) iterator.next();
                EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause, EntityPotionEffectEvent.Action.CLEARED);
                if (event.isCancelled()) {
                    continue;
                }
                this.onEffectRemoved(effect);
                // CraftBukkit end
                iterator.remove();
            }

            return flag;
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<MobEffect, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(MobEffect effect) {
        return this.activeEffects.containsKey(effect);
    }

    @Nullable
    public MobEffectInstance getEffect(MobEffect effect) {
        return (MobEffectInstance) this.activeEffects.get(effect);
    }

    public final boolean addEffect(MobEffectInstance effect) {
        return this.addEffect(effect, (Entity) null);
    }

    // CraftBukkit start
    public boolean addEffect(MobEffectInstance mobeffect, EntityPotionEffectEvent.Cause cause) {
        return this.addEffect(mobeffect, (Entity) null, cause);
    }

    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return this.addEffect(effect, source, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean addEffect(MobEffectInstance mobeffect, @Nullable Entity entity, EntityPotionEffectEvent.Cause cause) {
        org.spigotmc.AsyncCatcher.catchOp("effect add"); // Spigot
        if (this.isTickingEffects) {
            this.effectsToProcess.add(new ProcessableEffect(mobeffect, cause));
            return true;
        }
        // CraftBukkit end

        if (!this.canBeAffected(mobeffect)) {
            return false;
        } else {
            MobEffectInstance mobeffect1 = (MobEffectInstance) this.activeEffects.get(mobeffect.getEffect());

            // CraftBukkit start
            boolean override = false;
            if (mobeffect1 != null) {
                override = new MobEffectInstance(mobeffect1).update(mobeffect);
            }

            EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobeffect1, mobeffect, cause, override);
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end

            if (mobeffect1 == null) {
                this.activeEffects.put(mobeffect.getEffect(), mobeffect);
                this.onEffectAdded(mobeffect, entity);
                return true;
                // CraftBukkit start
            } else if (event.isOverride()) {
                mobeffect1.update(mobeffect);
                this.onEffectUpdated(mobeffect1, true, entity);
                // CraftBukkit end
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean canBeAffected(MobEffectInstance effect) {
        if (this.getMobType() == MobType.UNDEAD) {
            MobEffect mobeffectlist = effect.getEffect();

            if (mobeffectlist == MobEffects.REGENERATION || mobeffectlist == MobEffects.POISON) {
                return false;
            }
        }

        return true;
    }

    public void forceAddEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (this.canBeAffected(effect)) {
            MobEffectInstance mobeffect1 = (MobEffectInstance) this.activeEffects.put(effect.getEffect(), effect);

            if (mobeffect1 == null) {
                this.onEffectAdded(effect, source);
            } else {
                this.onEffectUpdated(effect, true, source);
            }

        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getMobType() == MobType.UNDEAD;
    }

    // CraftBukkit start
    @Nullable
    public MobEffectInstance removeEffectNoUpdate(@Nullable MobEffect type) {
        return this.c(type, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    @Nullable
    public MobEffectInstance c(@Nullable MobEffect mobeffectlist, EntityPotionEffectEvent.Cause cause) {
        if (this.isTickingEffects) {
            this.effectsToProcess.add(new ProcessableEffect(mobeffectlist, cause));
            return null;
        }

        MobEffectInstance effect = this.activeEffects.get(mobeffectlist);
        if (effect == null) {
            return null;
        }

        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause);
        if (event.isCancelled()) {
            return null;
        }

        return (MobEffectInstance) this.activeEffects.remove(mobeffectlist);
    }

    public boolean removeEffect(MobEffect type) {
        return this.removeEffect(type, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeEffect(MobEffect mobeffectlist, EntityPotionEffectEvent.Cause cause) {
        MobEffectInstance mobeffect = this.c(mobeffectlist, cause);
        // CraftBukkit end

        if (mobeffect != null) {
            this.onEffectRemoved(mobeffect);
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        this.effectsDirty = true;
        if (!this.level.isClientSide) {
            effect.getEffect().addAttributeModifiers(this, this.getAttributes(), effect.getAmplifier());
        }

    }

    protected void onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, @Nullable Entity source) {
        this.effectsDirty = true;
        if (reapplyEffect && !this.level.isClientSide) {
            MobEffect mobeffectlist = effect.getEffect();

            mobeffectlist.removeAttributeModifiers(this, this.getAttributes(), effect.getAmplifier());
            mobeffectlist.addAttributeModifiers(this, this.getAttributes(), effect.getAmplifier());
        }

    }

    protected void onEffectRemoved(MobEffectInstance effect) {
        this.effectsDirty = true;
        if (!this.level.isClientSide) {
            effect.getEffect().removeAttributeModifiers(this, this.getAttributes(), effect.getAmplifier());
        }

    }

    // CraftBukkit start - Delegate so we can handle providing a reason for health being regained
    public void heal(float amount) {
        this.heal(amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    public void heal(float f, EntityRegainHealthEvent.RegainReason regainReason) {
        // Paper start - Forward
        heal(f, regainReason, false);
    }

    public void heal(float f, EntityRegainHealthEvent.RegainReason regainReason, boolean isFastRegen) {
        // Paper end
        float f1 = this.getHealth();

        if (f1 > 0.0F) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), f, regainReason, isFastRegen); // Paper
            // Suppress during worldgen
            if (this.valid) {
                this.level.getCraftServer().getPluginManager().callEvent(event);
            }

            if (!event.isCancelled()) {
                this.setHealth((float) (this.getHealth() + event.getAmount()));
            }
            // CraftBukkit end
        }

    }

    public float getHealth() {
        // CraftBukkit start - Use unscaled health
        if (this instanceof ServerPlayer) {
            return (float) ((ServerPlayer) this).getBukkitEntity().getHealth();
        }
        // CraftBukkit end
        return (Float) this.entityData.get(LivingEntity.DATA_HEALTH_ID);
    }

    public void setHealth(float health) {
        // Paper start
        if (Float.isNaN(health)) { health = getMaxHealth(); if (this.valid) {
            System.err.println("[NAN-HEALTH] " + getScoreboardName() + " had NaN health set");
        } } // Paper end
        // CraftBukkit start - Handle scaled health
        if (this instanceof ServerPlayer) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = ((ServerPlayer) this).getBukkitEntity();
            // Squeeze
            if (health < 0.0F) {
                player.setRealHealth(0.0D);
            } else if (health > player.getMaxHealth()) {
                player.setRealHealth(player.getMaxHealth());
            } else {
                player.setRealHealth(health);
            }

            player.updateScaledHealth(false);
            return;
        }
        // CraftBukkit end
        this.entityData.set(LivingEntity.DATA_HEALTH_ID, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (this.level.isClientSide) {
            return false;
        } else if (this.isRemoved() || this.dead || this.getHealth() <= 0.0F) { // CraftBukkit - Don't allow entities that got set to dead/killed elsewhere to get damaged and die
            return false;
        } else if (source.isFire() && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping() && !this.level.isClientSide) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            float f1 = amount;
            boolean flag = amount > 0.0F && this.isDamageSourceBlocked(source); // Copied from below
            float f2 = 0.0F;

            // CraftBukkit - Moved into damageEntity0(DamageSource, float)
            if (false && amount > 0.0F && this.isDamageSourceBlocked(source)) {
                this.hurtCurrentlyUsedShield(amount);
                f2 = amount;
                amount = 0.0F;
                if (!source.isProjectile()) {
                    Entity entity = source.getDirectEntity();

                    if (entity instanceof LivingEntity) {
                        LivingEntity entityliving = (LivingEntity) entity;

                        this.blockUsingShield(entityliving);
                    }
                }

                flag = true;
            }

            this.animationSpeed = 1.5F;
            boolean flag1 = true;

            if ((float) this.invulnerableTime > (float) this.invulnerableDuration / 2.0F) { // CraftBukkit - restore use of maxNoDamageTicks
                if (amount <= this.lastHurt) {
                    return false;
                }

                // CraftBukkit start
                if (!this.damageEntity0(source, amount - this.lastHurt)) {
                    return false;
                }
                // CraftBukkit end
                this.lastHurt = amount;
                flag1 = false;
            } else {
                // CraftBukkit start
                if (!this.damageEntity0(source, amount)) {
                    return false;
                }
                this.lastHurt = amount;
                this.invulnerableTime = this.invulnerableDuration; // CraftBukkit - restore use of maxNoDamageTicks
                // this.damageEntity0(damagesource, f);
                // CraftBukkit end
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            // CraftBukkit - Moved into damageEntity0(DamageSource, float)
            if (false && source.isDamageHelmet() && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(source, amount);
                amount *= 0.75F;
            }

            // CraftBukkit start
            if (this instanceof Animal) {
                ((Animal) this).resetLove();
                if (this instanceof TamableAnimal) {
                    ((TamableAnimal) this).setOrderedToSit(false);
                }
            }
            // CraftBukkit end

            this.hurtDir = 0.0F;
            Entity entity1 = source.getEntity();

            if (entity1 != null) {
                if (entity1 instanceof LivingEntity && !source.isNoAggro()) {
                    this.setLastHurtByMob((LivingEntity) entity1);
                }

                if (entity1 instanceof net.minecraft.world.entity.player.Player) {
                    this.lastHurtByPlayerTime = 100;
                    this.lastHurtByPlayer = (net.minecraft.world.entity.player.Player) entity1;
                } else if (entity1 instanceof Wolf) {
                    Wolf entitywolf = (Wolf) entity1;

                    if (entitywolf.isTame()) {
                        this.lastHurtByPlayerTime = 100;
                        LivingEntity entityliving1 = entitywolf.getOwner();

                        if (entityliving1 != null && entityliving1.getType() == EntityType.PLAYER) {
                            this.lastHurtByPlayer = (net.minecraft.world.entity.player.Player) entityliving1;
                        } else {
                            this.lastHurtByPlayer = null;
                        }
                    }
                }
            }

            boolean knockbackCancelled = level.paperConfig().environment.disableExplosionKnockback && source.isExplosion() && this instanceof net.minecraft.world.entity.player.Player; // Paper - Disable explosion knockback
            if (flag1) {
                if (flag) {
                    this.level.broadcastEntityEvent(this, (byte) 29);
                } else if (source instanceof EntityDamageSource && ((EntityDamageSource) source).isThorns()) {
                    this.level.broadcastEntityEvent(this, (byte) 33);
                } else {
                    byte b0;

                    if (source == DamageSource.DROWN) {
                        b0 = 36;
                    } else if (source.isFire()) {
                        b0 = 37;
                    } else if (source == DamageSource.SWEET_BERRY_BUSH) {
                        b0 = 44;
                    } else if (source == DamageSource.FREEZE) {
                        b0 = 57;
                    } else {
                        b0 = 2;
                    }

                    if (!knockbackCancelled) // Paper - Disable explosion knockback
                    this.level.broadcastEntityEvent(this, b0);
                }

                if (source != DamageSource.DROWN && (!flag || amount > 0.0F)) {
                    this.markHurt();
                }

                if (entity1 != null && !source.isExplosion()) {
                    double d0 = entity1.getX() - this.getX();

                    double d1;

                    for (d1 = entity1.getZ() - this.getZ(); d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
                        d0 = (Math.random() - Math.random()) * 0.01D;
                    }

                    this.hurtDir = (float) (Mth.atan2(d1, d0) * 57.2957763671875D - (double) this.getYRot());
                    this.knockback(0.4000000059604645D, d0, d1);
                } else {
                    this.hurtDir = (float) ((int) (Math.random() * 2.0D) * 180);
                }
            }

            if (knockbackCancelled) this.level.broadcastEntityEvent(this, (byte) 2); // Paper - Disable explosion knockback
            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(source)) {
                    SoundEvent soundeffect = this.getDeathSound();

                    if (flag1 && soundeffect != null) {
                        this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
                    }

                    this.die(source);
                }
            } else if (flag1) {
                this.playHurtSound(source);
            }

            boolean flag2 = !flag || amount > 0.0F;

            if (flag2) {
                this.lastDamageSource = source;
                this.lastDamageStamp = this.level.getGameTime();
            }

            if (this instanceof ServerPlayer) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer) this, source, f1, amount, flag);
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    ((ServerPlayer) this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f2 * 10.0F));
                }
            }

            if (entity1 instanceof ServerPlayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer) entity1, this, source, f1, amount, flag);
            }

            return flag2;
        }
    }

    protected void blockUsingShield(LivingEntity attacker) {
        attacker.blockedByShield(this);
    }

    protected void blockedByShield(LivingEntity target) {
        target.knockback(0.5D, target.getX() - this.getX(), target.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource source) {
        if (source.isBypassInvul()) {
            return false;
        } else {
            ItemStack itemstack = null;
            InteractionHand[] aenumhand = InteractionHand.values();
            int i = aenumhand.length;

            // CraftBukkit start
            InteractionHand hand = null;
            ItemStack itemstack1 = ItemStack.EMPTY;
            for (int j = 0; j < i; ++j) {
                InteractionHand enumhand = aenumhand[j];
                itemstack1 = this.getItemInHand(enumhand);

                if (itemstack1.is(Items.TOTEM_OF_UNDYING)) {
                    hand = enumhand; // CraftBukkit
                    itemstack = itemstack1.copy();
                    // itemstack1.subtract(1); // CraftBukkit
                    break;
                }
            }

            org.bukkit.inventory.EquipmentSlot handSlot = (hand != null) ? org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand) : null;
            EntityResurrectEvent event = new EntityResurrectEvent((org.bukkit.entity.LivingEntity) this.getBukkitEntity(), handSlot);
            event.setCancelled(itemstack == null);
            this.level.getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                if (!itemstack1.isEmpty()) {
                    itemstack1.shrink(1);
                }
                if (itemstack != null && this instanceof ServerPlayer) {
                    // CraftBukkit end
                    ServerPlayer entityplayer = (ServerPlayer) this;

                    entityplayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                    CriteriaTriggers.USED_TOTEM.trigger(entityplayer, itemstack);
                }

                this.setHealth(1.0F);
                // CraftBukkit start
                this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                // CraftBukkit end
                this.level.broadcastEntityEvent(this, (byte) 35);
            }

            return !event.isCancelled();
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level.getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource source) {
        SoundEvent soundeffect = this.getHurtSound(source);

        if (soundeffect != null) {
            this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    public boolean isDamageSourceBlocked(DamageSource source) {
        Entity entity = source.getDirectEntity();
        boolean flag = false;

        if (entity instanceof AbstractArrow) {
            AbstractArrow entityarrow = (AbstractArrow) entity;

            if (entityarrow.getPierceLevel() > 0) {
                flag = true;
            }
        }

        if (!source.isBypassArmor() && this.isBlocking() && !flag) {
            Vec3 vec3d = source.getSourcePosition();

            if (vec3d != null) {
                Vec3 vec3d1 = this.getViewVector(1.0F);
                Vec3 vec3d2 = vec3d.vectorTo(this.position()).normalize();

                vec3d2 = new Vec3(vec3d2.x, 0.0D, vec3d2.z);
                if (vec3d2.dot(vec3d1) < 0.0D) {
                    return true;
                }
            }
        }

        return false;
    }

    private void breakItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (!this.isSilent()) {
                this.level.playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK, this.getSoundSource(), 0.8F, 0.8F + this.level.random.nextFloat() * 0.4F, false);
            }

            this.spawnItemParticles(stack, 5);
        }

    }

    public void die(DamageSource damageSource) {
        if (!this.isRemoved() && !this.dead) {
            Entity entity = damageSource.getEntity();
            LivingEntity entityliving = this.getKillCredit();

            if (this.deathScore >= 0 && entityliving != null) {
                entityliving.awardKillScore(this, this.deathScore, damageSource);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            if (!this.level.isClientSide && this.hasCustomName()) {
                if (org.spigotmc.SpigotConfig.logNamedDeaths) LivingEntity.LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString()); // Spigot
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            if (this.level instanceof ServerLevel) {
                if (entity == null || entity.wasKilled((ServerLevel) this.level, this)) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    this.dropAllDeathLoot(damageSource);
                    this.createWitherRose(entityliving);
                }

                this.level.broadcastEntityEvent(this, (byte) 3);
            }

            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable LivingEntity adversary) {
        if (!this.level.isClientSide) {
            boolean flag = false;

            if (adversary instanceof WitherBoss) {
                if (this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    BlockPos blockposition = this.blockPosition();
                    BlockState iblockdata = Blocks.WITHER_ROSE.defaultBlockState();

                    if (this.level.getBlockState(blockposition).isAir() && iblockdata.canSurvive(this.level, blockposition)) {
                        // CraftBukkit start - call EntityBlockFormEvent for Wither Rose
                        flag = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this.level, blockposition, iblockdata, 3, this);
                        // CraftBukkit end
                    }
                }

                if (!flag) {
                    ItemEntity entityitem = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));

                    // CraftBukkit start
                    org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
                    CraftEventFactory.callEvent(event);
                    if (event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.level.addFreshEntity(entityitem);
                }
            }

        }
    }

    protected void dropAllDeathLoot(DamageSource source) {
        Entity entity = source.getEntity();
        int i;

        if (entity instanceof net.minecraft.world.entity.player.Player) {
            i = EnchantmentHelper.getMobLooting((LivingEntity) entity);
        } else {
            i = 0;
        }

        boolean flag = this.lastHurtByPlayerTime > 0;

        this.dropEquipment(); // CraftBukkit - from below
        if (this.shouldDropLoot() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(source, flag);
            this.dropCustomDeathLoot(source, i, flag);
        }
        // CraftBukkit start - Call death event
        CraftEventFactory.callEntityDeathEvent(this, this.drops);
        this.drops = new ArrayList<>();
        // CraftBukkit end

        // this.dropInventory();// CraftBukkit - moved up
        this.dropExperience();
    }

    protected void dropEquipment() {}

    // CraftBukkit start
    public int getExpReward() {
        if (this.level instanceof ServerLevel && !this.wasExperienceConsumed() && (this.isAlwaysExperienceDropper() || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) {
            int i = this.getExperienceReward();
            return i;
        } else {
            return 0;
        }
    }
    // CraftBukkit end

    protected void dropExperience() {
        // CraftBukkit start - Update getExpReward() above if the removed if() changes!
        if (true && !(this instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)) { // CraftBukkit - SPIGOT-2420: Special case ender dragon will drop the xp over time
            ExperienceOrb.award((ServerLevel) this.level, this.position(), this.expToDrop);
            this.expToDrop = 0;
        }
        // CraftBukkit end

    }

    protected void dropCustomDeathLoot(DamageSource source, int lootingMultiplier, boolean allowDrops) {}

    public ResourceLocation getLootTable() {
        return this.getType().getDefaultLootTable();
    }

    protected void dropFromLootTable(DamageSource source, boolean causedByPlayer) {
        ResourceLocation minecraftkey = this.getLootTable();
        LootTable loottable = this.level.getServer().getLootTables().get(minecraftkey);
        LootContext.Builder loottableinfo_builder = this.createLootContext(causedByPlayer, source);

        loottable.getRandomItems(loottableinfo_builder.create(LootContextParamSets.ENTITY), this::spawnAtLocation);
    }

    protected LootContext.Builder createLootContext(boolean causedByPlayer, DamageSource source) {
        LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.level)).withRandom(this.random).withParameter(LootContextParams.THIS_ENTITY, this).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.DAMAGE_SOURCE, source).withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity()).withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());

        if (causedByPlayer && this.lastHurtByPlayer != null) {
            loottableinfo_builder = loottableinfo_builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer).withLuck(this.lastHurtByPlayer.getLuck());
        }

        return loottableinfo_builder;
    }

    public void knockback(double strength, double x, double z) {
        strength *= 1.0D - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (strength > 0.0D) {
            this.hasImpulse = true;
            Vec3 vec3d = this.getDeltaMovement();
            Vec3 vec3d1 = (new Vec3(x, 0.0D, z)).normalize().scale(strength);

            this.setDeltaMovement(vec3d.x / 2.0D - vec3d1.x, this.onGround ? Math.min(0.4D, vec3d.y / 2.0D + strength) : vec3d.y, vec3d.z / 2.0D - vec3d1.z);
        }
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    public SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    private SoundEvent getFallDamageSound(int distance) {
        return distance > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    protected Vec3 getMeleeAttackReferencePosition() {
        Entity entity = this.getVehicle();

        if (entity instanceof RiderShieldingMount) {
            RiderShieldingMount ridershieldingmount = (RiderShieldingMount) entity;

            return this.position().add(0.0D, ridershieldingmount.getRiderShieldingHeight(), 0.0D);
        } else {
            return this.position();
        }
    }

    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.GENERIC_SMALL_FALL, SoundEvents.GENERIC_BIG_FALL);
    }

    protected SoundEvent getDrinkingSound(ItemStack stack) {
        return stack.getDrinkingSound();
    }

    public SoundEvent getEatingSound(ItemStack stack) {
        return stack.getEatingSound();
    }

    // CraftBukkit start - Add delegate methods
    public SoundEvent getHurtSound0(DamageSource damagesource) {
        return this.getHurtSound(damagesource);
    }

    public SoundEvent getDeathSound0() {
        return this.getDeathSound();
    }

    public SoundEvent getFallDamageSound0(int fallHeight) {
        return this.getFallDamageSound(fallHeight);
    }

    public SoundEvent getDrinkingSound0(ItemStack itemstack) {
        return this.getDrinkingSound(itemstack);
    }

    public SoundEvent getEatingSound0(ItemStack itemstack) {
        return this.getEatingSound(itemstack);
    }
    // CraftBukkit end

    @Override
    public void setOnGround(boolean onGround) {
        super.setOnGround(onGround);
        if (onGround) {
            this.lastClimbablePos = Optional.empty();
        }

    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockposition = this.blockPosition();
            BlockState iblockdata = this.getFeetBlockState();

            if (iblockdata.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockposition);
                return true;
            } else if (iblockdata.getBlock() instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockposition, iblockdata)) {
                this.lastClimbablePos = Optional.of(blockposition);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPos pos, BlockState state) {
        if ((Boolean) state.getValue(TrapDoorBlock.OPEN)) {
            BlockState iblockdata1 = this.level.getBlockState(pos.below());

            if (iblockdata1.is(Blocks.LADDER) && iblockdata1.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        boolean flag = super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
        int i = this.calculateFallDamage(fallDistance, damageMultiplier);

        if (i > 0) {
            // CraftBukkit start
            if (!this.hurt(damageSource, (float) i)) {
                return true;
            }
            // CraftBukkit end
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            // this.damageEntity(damagesource, (float) i); // CraftBukkit - moved up
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(float fallDistance, float damageMultiplier) {
        MobEffectInstance mobeffect = this.getEffect(MobEffects.JUMP);
        float f2 = mobeffect == null ? 0.0F : (float) (mobeffect.getAmplifier() + 1);

        return Mth.ceil((fallDistance - 3.0F - f2) * damageMultiplier);
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - 0.20000000298023224D);
            int k = Mth.floor(this.getZ());
            BlockState iblockdata = this.level.getBlockState(new BlockPos(i, j, k));

            if (!iblockdata.isAir()) {
                SoundType soundeffecttype = iblockdata.getSoundType();

                this.playSound(soundeffecttype.getFallSound(), soundeffecttype.getVolume() * 0.5F, soundeffecttype.getPitch() * 0.75F);
            }

        }
    }

    @Override
    public void animateHurt() {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        this.hurtDir = 0.0F;
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource source, float amount) {}

    protected void hurtHelmet(DamageSource source, float amount) {}

    protected void hurtCurrentlyUsedShield(float amount) {}

    protected float getDamageAfterArmorAbsorb(DamageSource source, float amount) {
        if (!source.isBypassArmor()) {
            // this.damageArmor(damagesource, f); // CraftBukkit - Moved into damageEntity0(DamageSource, float)
            amount = CombatRules.getDamageAfterAbsorb(amount, (float) this.getArmorValue(), (float) this.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }

        return amount;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        if (source.isBypassMagic()) {
            return amount;
        } else {
            int i;

            // CraftBukkit - Moved to damageEntity0(DamageSource, float)
            if (false && this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && source != DamageSource.OUT_OF_WORLD) {
                i = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - i;
                float f1 = amount * (float) j;
                float f2 = amount;

                amount = Math.max(f1 / 25.0F, 0.0F);
                float f3 = f2 - amount;

                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (source.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer) source.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            if (amount <= 0.0F) {
                return 0.0F;
            } else if (source.isBypassEnchantments()) {
                return amount;
            } else {
                i = EnchantmentHelper.getDamageProtection(this.getArmorSlots(), source);
                if (i > 0) {
                    amount = CombatRules.getDamageAfterMagicAbsorb(amount, (float) i);
                }

                return amount;
            }
        }
    }

    // CraftBukkit start
    protected boolean damageEntity0(final DamageSource damagesource, float f) { // void -> boolean, add final
       if (!this.isInvulnerableTo(damagesource)) {
            final boolean human = this instanceof net.minecraft.world.entity.player.Player;
            float originalDamage = f;
            Function<Double, Double> hardHat = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    if (damagesource.isDamageHelmet() && !LivingEntity.this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                        return -(f - (f * 0.75F));

                    }
                    return -0.0;
                }
            };
            float hardHatModifier = hardHat.apply((double) f).floatValue();
            f += hardHatModifier;

            Function<Double, Double> blocking = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -((LivingEntity.this.isDamageSourceBlocked(damagesource)) ? f : 0.0);
                }
            };
            float blockingModifier = blocking.apply((double) f).floatValue();
            f += blockingModifier;

            Function<Double, Double> armor = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -(f - LivingEntity.this.getDamageAfterArmorAbsorb(damagesource, f.floatValue()));
                }
            };
            float armorModifier = armor.apply((double) f).floatValue();
            f += armorModifier;

            Function<Double, Double> resistance = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    if (!damagesource.isBypassMagic() && LivingEntity.this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && damagesource != DamageSource.OUT_OF_WORLD) {
                        int i = (LivingEntity.this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                        int j = 25 - i;
                        float f1 = f.floatValue() * (float) j;
                        return -(f - (f1 / 25.0F));
                    }
                    return -0.0;
                }
            };
            float resistanceModifier = resistance.apply((double) f).floatValue();
            f += resistanceModifier;

            Function<Double, Double> magic = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -(f - LivingEntity.this.getDamageAfterMagicAbsorb(damagesource, f.floatValue()));
                }
            };
            float magicModifier = magic.apply((double) f).floatValue();
            f += magicModifier;

            Function<Double, Double> absorption = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -(Math.max(f - Math.max(f - LivingEntity.this.getAbsorptionAmount(), 0.0F), 0.0F));
                }
            };
            float absorptionModifier = absorption.apply((double) f).floatValue();

            EntityDamageEvent event = CraftEventFactory.handleLivingEntityDamageEvent(this, damagesource, originalDamage, hardHatModifier, blockingModifier, armorModifier, resistanceModifier, magicModifier, absorptionModifier, hardHat, blocking, armor, resistance, magic, absorption);
            if (damagesource.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                ((net.minecraft.world.entity.player.Player) damagesource.getEntity()).resetAttackStrengthTicker(); // Moved from EntityHuman in order to make the cooldown reset get called after the damage event is fired
            }
            if (event.isCancelled()) {
                return false;
            }

            f = (float) event.getFinalDamage();

            // Resistance
            if (event.getDamage(DamageModifier.RESISTANCE) < 0) {
                float f3 = (float) -event.getDamage(DamageModifier.RESISTANCE);
                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damagesource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer) damagesource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            // Apply damage to helmet
            if (damagesource.isDamageHelmet() && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damagesource, f);
            }

            // Apply damage to armor
            if (!damagesource.isBypassArmor()) {
                float armorDamage = (float) (event.getDamage() + event.getDamage(DamageModifier.BLOCKING) + event.getDamage(DamageModifier.HARD_HAT));
                this.hurtArmor(damagesource, armorDamage);
            }

            // Apply blocking code // PAIL: steal from above
            if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                this.level.broadcastEntityEvent(this, (byte) 29); // SPIGOT-4635 - shield damage sound
                this.hurtCurrentlyUsedShield((float) -event.getDamage(DamageModifier.BLOCKING));
                Entity entity = damagesource.getDirectEntity();

                if (entity instanceof LivingEntity) {
                    this.blockUsingShield((LivingEntity) entity);
                }
            }

            absorptionModifier = (float) -event.getDamage(DamageModifier.ABSORPTION);
            this.setAbsorptionAmount(Math.max(this.getAbsorptionAmount() - absorptionModifier, 0.0F));
            float f2 = absorptionModifier;

            if (f2 > 0.0F && f2 < 3.4028235E37F && this instanceof net.minecraft.world.entity.player.Player) {
                ((net.minecraft.world.entity.player.Player) this).awardStat(Stats.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }
            if (f2 > 0.0F && f2 < 3.4028235E37F && damagesource.getEntity() instanceof ServerPlayer) {
                ((ServerPlayer) damagesource.getEntity()).awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f2 * 10.0F));
            }

            if (f > 0 || !human) {
                if (human) {
                    // PAIL: Be sure to drag all this code from the EntityHuman subclass each update.
                    ((net.minecraft.world.entity.player.Player) this).causeFoodExhaustion(damagesource.getFoodExhaustion(), org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                    if (f < 3.4028235E37F) {
                        ((net.minecraft.world.entity.player.Player) this).awardStat(Stats.DAMAGE_TAKEN, Math.round(f * 10.0F));
                    }
                }
                // CraftBukkit end
                float f3 = this.getHealth();

                this.setHealth(f3 - f);
                this.getCombatTracker().recordDamage(damagesource, f3, f);
                // CraftBukkit start
                if (!human) {
                    this.setAbsorptionAmount(this.getAbsorptionAmount() - f);
                }
                this.gameEvent(GameEvent.ENTITY_DAMAGE);

                return true;
            } else {
                // Duplicate triggers if blocking
                if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                    if (this instanceof ServerPlayer) {
                        CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer) this, damagesource, f, originalDamage, true);
                        f2 = (float) -event.getDamage(DamageModifier.BLOCKING);
                        if (f2 > 0.0F && f2 < 3.4028235E37F) {
                            ((ServerPlayer) this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(originalDamage * 10.0F));
                        }
                    }

                    if (damagesource.getEntity() instanceof ServerPlayer) {
                        CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer) damagesource.getEntity(), this, damagesource, f, originalDamage, true);
                    }

                    return false;
                } else {
                    return originalDamage > 0;
                }
                // CraftBukkit end
            }
        }
        return false; // CraftBukkit
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public LivingEntity getKillCredit() {
        return (LivingEntity) (this.combatTracker.getKiller() != null ? this.combatTracker.getKiller() : (this.lastHurtByPlayer != null ? this.lastHurtByPlayer : (this.lastHurtByMob != null ? this.lastHurtByMob : null)));
    }

    public final float getMaxHealth() {
        return (float) this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final int getArrowCount() {
        return (Integer) this.entityData.get(LivingEntity.DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int stuckArrowCount) {
        // CraftBukkit start
        this.setArrowCount(stuckArrowCount, false);
    }

    public final void setArrowCount(int i, boolean flag) {
        ArrowBodyCountChangeEvent event = CraftEventFactory.callArrowBodyCountChangeEvent(this, this.getArrowCount(), i, flag);
        if (event.isCancelled()) {
            return;
        }
        this.entityData.set(LivingEntity.DATA_ARROW_COUNT_ID, event.getNewAmount());
    }
    // CraftBukkit end

    public final int getStingerCount() {
        return (Integer) this.entityData.get(LivingEntity.DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int stingerCount) {
        this.entityData.set(LivingEntity.DATA_STINGER_COUNT_ID, stingerCount);
    }

    private int getCurrentSwingDuration() {
        return MobEffectUtil.hasDigSpeed(this) ? 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this)) : (this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6);
    }

    public void swing(InteractionHand hand) {
        this.swing(hand, false);
    }

    public void swing(InteractionHand hand, boolean fromServerPlayer) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = hand;
            if (this.level instanceof ServerLevel) {
                ClientboundAnimatePacket packetplayoutanimation = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache chunkproviderserver = ((ServerLevel) this.level).getChunkSource();

                if (fromServerPlayer) {
                    chunkproviderserver.broadcastAndSend(this, packetplayoutanimation);
                } else {
                    chunkproviderserver.broadcast(this, packetplayoutanimation);
                }
            }
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        switch (status) {
            case 2:
            case 33:
            case 36:
            case 37:
            case 44:
            case 57:
                this.animationSpeed = 1.5F;
                this.invulnerableTime = 20;
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
                this.hurtDir = 0.0F;
                if (status == 33) {
                    this.playSound(SoundEvents.THORNS_HIT, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                DamageSource damagesource;

                if (status == 37) {
                    damagesource = DamageSource.ON_FIRE;
                } else if (status == 36) {
                    damagesource = DamageSource.DROWN;
                } else if (status == 44) {
                    damagesource = DamageSource.SWEET_BERRY_BUSH;
                } else if (status == 57) {
                    damagesource = DamageSource.FREEZE;
                } else {
                    damagesource = DamageSource.GENERIC;
                }

                SoundEvent soundeffect = this.getHurtSound(damagesource);

                if (soundeffect != null) {
                    this.playSound(soundeffect, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                this.hurt(DamageSource.GENERIC, 0.0F);
                this.lastDamageSource = damagesource;
                this.lastDamageStamp = this.level.getGameTime();
                break;
            case 3:
                SoundEvent soundeffect1 = this.getDeathSound();

                if (soundeffect1 != null) {
                    this.playSound(soundeffect1, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof net.minecraft.world.entity.player.Player)) {
                    this.setHealth(0.0F);
                    this.die(DamageSource.GENERIC);
                }
                break;
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 31:
            case 32:
            case 34:
            case 35:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 45:
            case 53:
            case 56:
            case 58:
            case 59:
            default:
                super.handleEntityEvent(status);
                break;
            case 29:
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.level.random.nextFloat() * 0.4F);
                break;
            case 30:
                this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level.random.nextFloat() * 0.4F);
                break;
            case 46:
                boolean flag = true;

                for (int i = 0; i < 128; ++i) {
                    double d0 = (double) i / 127.0D;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double d1 = Mth.lerp(d0, this.xo, this.getX()) + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth() * 2.0D;
                    double d2 = Mth.lerp(d0, this.yo, this.getY()) + this.random.nextDouble() * (double) this.getBbHeight();
                    double d3 = Mth.lerp(d0, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth() * 2.0D;

                    this.level.addParticle(ParticleTypes.PORTAL, d1, d2, d3, (double) f, (double) f1, (double) f2);
                }

                return;
            case 47:
                this.breakItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EquipmentSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EquipmentSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EquipmentSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EquipmentSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EquipmentSlot.FEET));
                break;
            case 54:
                HoneyBlock.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
        }

    }

    private void makePoofParticles() {
        for (int i = 0; i < 20; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level.addParticle(ParticleTypes.POOF, this.getRandomX(1.0D), this.getRandomY(), this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    private void swapHandItems() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.OFFHAND);

        this.setItemSlot(EquipmentSlot.OFFHAND, this.getItemBySlot(EquipmentSlot.MAINHAND));
        this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
    }

    @Override
    protected void outOfWorld() {
        this.hurt(DamageSource.OUT_OF_WORLD, 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();

        if (this.swinging) {
            ++this.swingTime;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float) this.swingTime / (float) i;
    }

    @Nullable
    public AttributeInstance getAttribute(Attribute attribute) {
        return this.getAttributes().getInstance(attribute);
    }

    public double getAttributeValue(Holder<Attribute> attribute) {
        return this.getAttributeValue((Attribute) attribute.value());
    }

    public double getAttributeValue(Attribute attribute) {
        return this.getAttributes().getValue(attribute);
    }

    public double getAttributeBaseValue(Holder<Attribute> attribute) {
        return this.getAttributeBaseValue((Attribute) attribute.value());
    }

    public double getAttributeBaseValue(Attribute attribute) {
        return this.getAttributes().getBaseValue(attribute);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    public boolean isHolding(Item item) {
        return this.isHolding((itemstack) -> {
            return itemstack.is(item);
        });
    }

    public boolean isHolding(Predicate<ItemStack> predicate) {
        return predicate.test(this.getMainHandItem()) || predicate.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (hand == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + hand);
        }
    }

    public void setItemInHand(InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        } else {
            if (hand != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + hand);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, stack);
        }

    }

    public boolean hasItemInSlot(EquipmentSlot slot) {
        return !this.getItemBySlot(slot).isEmpty();
    }

    @Override
    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot slot);

    // CraftBukkit start
    public void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack, boolean silent) {
        this.setItemSlot(enumitemslot, itemstack);
    }
    // CraftBukkit end

    @Override
    public abstract void setItemSlot(EquipmentSlot slot, ItemStack stack);

    protected void verifyEquippedItem(ItemStack stack) {
        CompoundTag nbttagcompound = stack.getTag();

        if (nbttagcompound != null) {
            stack.getItem().verifyTagAfterLoad(nbttagcompound);
        }

    }

    public float getArmorCoverPercentage() {
        Iterable<ItemStack> iterable = this.getArmorSlots();
        int i = 0;
        int j = 0;

        for (Iterator iterator = iterable.iterator(); iterator.hasNext(); ++i) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (!itemstack.isEmpty()) {
                ++j;
            }
        }

        return i > 0 ? (float) j / (float) i : 0.0F;
    }

    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributemodifiable.getModifier(LivingEntity.SPEED_MODIFIER_SPRINTING_UUID) != null) {
            attributemodifiable.removeModifier(LivingEntity.SPEED_MODIFIER_SPRINTING);
        }

        if (sprinting) {
            attributemodifiable.addTransientModifier(LivingEntity.SPEED_MODIFIER_SPRINTING);
        }

    }

    public float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }

    }

    private void dismountVehicle(Entity vehicle) {
        Vec3 vec3d;

        if (this.isRemoved()) {
            vec3d = this.position();
        } else if (!vehicle.isRemoved() && !this.level.getBlockState(vehicle.blockPosition()).is(BlockTags.PORTALS)) {
            vec3d = vehicle.getDismountLocationForPassenger(this);
        } else {
            double d0 = Math.max(this.getY(), vehicle.getY());

            vec3d = new Vec3(this.getX(), d0, this.getZ());
        }

        this.dismountTo(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return 0.42F * this.getBlockJumpFactor();
    }

    public double getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP) ? (double) (0.1F * (float) (this.getEffect(MobEffects.JUMP).getAmplifier() + 1)) : 0.0D;
    }

    protected void jumpFromGround() {
        double d0 = (double) this.getJumpPower() + this.getJumpBoostPower();
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
        if (this.isSprinting()) {
            float f = this.getYRot() * 0.017453292F;

            this.setDeltaMovement(this.getDeltaMovement().add((double) (-Mth.sin(f) * 0.2F), 0.0D, (double) (Mth.cos(f) * 0.2F)));
        }

        this.hasImpulse = true;
    }

    protected void goDownInWater() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03999999910593033D, 0.0D));
    }

    protected void jumpInLiquid(TagKey<Fluid> fluid) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03999999910593033D, 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(FluidState state) {
        return false;
    }

    public void travel(Vec3 movementInput) {
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            double d0 = 0.08D;
            boolean flag = this.getDeltaMovement().y <= 0.0D;

            if (flag && this.hasEffect(MobEffects.SLOW_FALLING)) {
                d0 = 0.01D;
                this.resetFallDistance();
            }

            FluidState fluid = this.level.getFluidState(this.blockPosition());
            double d1;
            float f;

            if (this.isInWater() && this.isAffectedByFluids() && !this.canStandOnFluid(fluid)) {
                d1 = this.getY();
                f = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f1 = 0.02F;
                float f2 = (float) EnchantmentHelper.getDepthStrider(this);

                if (f2 > 3.0F) {
                    f2 = 3.0F;
                }

                if (!this.onGround) {
                    f2 *= 0.5F;
                }

                if (f2 > 0.0F) {
                    f += (0.54600006F - f) * f2 / 3.0F;
                    f1 += (this.getSpeed() - f1) * f2 / 3.0F;
                }

                if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    f = 0.96F;
                }

                this.moveRelative(f1, movementInput);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec3d1 = this.getDeltaMovement();

                if (this.horizontalCollision && this.onClimbable()) {
                    vec3d1 = new Vec3(vec3d1.x, 0.2D, vec3d1.z);
                }

                this.setDeltaMovement(vec3d1.multiply((double) f, 0.800000011920929D, (double) f));
                Vec3 vec3d2 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());

                this.setDeltaMovement(vec3d2);
                if (this.horizontalCollision && this.isFree(vec3d2.x, vec3d2.y + 0.6000000238418579D - this.getY() + d1, vec3d2.z)) {
                    this.setDeltaMovement(vec3d2.x, 0.30000001192092896D, vec3d2.z);
                }
            } else if (this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluid)) {
                d1 = this.getY();
                this.moveRelative(0.02F, movementInput);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec3d3;

                if (this.getFluidHeight(FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.800000011920929D, 0.5D));
                    vec3d3 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());
                    this.setDeltaMovement(vec3d3);
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                }

                if (!this.isNoGravity()) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d0 / 4.0D, 0.0D));
                }

                vec3d3 = this.getDeltaMovement();
                if (this.horizontalCollision && this.isFree(vec3d3.x, vec3d3.y + 0.6000000238418579D - this.getY() + d1, vec3d3.z)) {
                    this.setDeltaMovement(vec3d3.x, 0.30000001192092896D, vec3d3.z);
                }
            } else if (this.isFallFlying()) {
                this.checkSlowFallDistance();
                Vec3 vec3d4 = this.getDeltaMovement();
                Vec3 vec3d5 = this.getLookAngle();

                f = this.getXRot() * 0.017453292F;
                double d2 = Math.sqrt(vec3d5.x * vec3d5.x + vec3d5.z * vec3d5.z);
                double d3 = vec3d4.horizontalDistance();
                double d4 = vec3d5.length();
                double d5 = Math.cos((double) f);

                d5 = d5 * d5 * Math.min(1.0D, d4 / 0.4D);
                vec3d4 = this.getDeltaMovement().add(0.0D, d0 * (-1.0D + d5 * 0.75D), 0.0D);
                double d6;

                if (vec3d4.y < 0.0D && d2 > 0.0D) {
                    d6 = vec3d4.y * -0.1D * d5;
                    vec3d4 = vec3d4.add(vec3d5.x * d6 / d2, d6, vec3d5.z * d6 / d2);
                }

                if (f < 0.0F && d2 > 0.0D) {
                    d6 = d3 * (double) (-Mth.sin(f)) * 0.04D;
                    vec3d4 = vec3d4.add(-vec3d5.x * d6 / d2, d6 * 3.2D, -vec3d5.z * d6 / d2);
                }

                if (d2 > 0.0D) {
                    vec3d4 = vec3d4.add((vec3d5.x / d2 * d3 - vec3d4.x) * 0.1D, 0.0D, (vec3d5.z / d2 * d3 - vec3d4.z) * 0.1D);
                }

                this.setDeltaMovement(vec3d4.multiply(0.9900000095367432D, 0.9800000190734863D, 0.9900000095367432D));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision && !this.level.isClientSide) {
                    d6 = this.getDeltaMovement().horizontalDistance();
                    double d7 = d3 - d6;
                    float f3 = (float) (d7 * 10.0D - 3.0D);

                    if (f3 > 0.0F) {
                        this.playSound(this.getFallDamageSound((int) f3), 1.0F, 1.0F);
                        this.hurt(DamageSource.FLY_INTO_WALL, f3);
                    }
                }

                if (this.onGround && !this.level.isClientSide) {
                    if (getSharedFlag(7) && !CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) // CraftBukkit
                    this.setSharedFlag(7, false);
                }
            } else {
                BlockPos blockposition = this.getBlockPosBelowThatAffectsMyMovement();
                float f4 = this.level.getBlockState(blockposition).getBlock().getFriction();

                f = this.onGround ? f4 * 0.91F : 0.91F;
                Vec3 vec3d6 = this.handleRelativeFrictionAndCalculateMovement(movementInput, f4);
                double d8 = vec3d6.y;

                if (this.hasEffect(MobEffects.LEVITATION)) {
                    d8 += (0.05D * (double) (this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - vec3d6.y) * 0.2D;
                    this.resetFallDistance();
                } else if (this.level.isClientSide && !this.level.hasChunkAt(blockposition)) {
                    if (this.getY() > (double) this.level.getMinBuildHeight()) {
                        d8 = -0.1D;
                    } else {
                        d8 = 0.0D;
                    }
                } else if (!this.isNoGravity()) {
                    d8 -= d0;
                }

                if (this.shouldDiscardFriction()) {
                    this.setDeltaMovement(vec3d6.x, d8, vec3d6.z);
                } else {
                    this.setDeltaMovement(vec3d6.x * (double) f, d8 * 0.9800000190734863D, vec3d6.z * (double) f);
                }
            }
        }

        this.calculateEntityAnimation(this, this instanceof FlyingAnimal);
    }

    public void calculateEntityAnimation(LivingEntity entity, boolean flutter) {
        entity.animationSpeedOld = entity.animationSpeed;
        double d0 = entity.getX() - entity.xo;
        double d1 = flutter ? entity.getY() - entity.yo : 0.0D;
        double d2 = entity.getZ() - entity.zo;
        float f = (float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 4.0F;

        if (f > 1.0F) {
            f = 1.0F;
        }

        entity.animationSpeed += (f - entity.animationSpeed) * 0.4F;
        entity.animationPosition += entity.animationSpeed;
    }

    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 movementInput, float slipperiness) {
        this.moveRelative(this.getFrictionInfluencedSpeed(slipperiness), movementInput);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec3d1 = this.getDeltaMovement();

        if ((this.horizontalCollision || this.jumping) && (this.onClimbable() || this.getFeetBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            vec3d1 = new Vec3(vec3d1.x, 0.2D, vec3d1.z);
        }

        return vec3d1;
    }

    public Vec3 getFluidFallingAdjustedMovement(double gravity, boolean falling, Vec3 motion) {
        if (!this.isNoGravity() && !this.isSprinting()) {
            double d1;

            if (falling && Math.abs(motion.y - 0.005D) >= 0.003D && Math.abs(motion.y - gravity / 16.0D) < 0.003D) {
                d1 = -0.003D;
            } else {
                d1 = motion.y - gravity / 16.0D;
            }

            return new Vec3(motion.x, d1, motion.z);
        } else {
            return motion;
        }
    }

    private Vec3 handleOnClimbable(Vec3 motion) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d0 = Mth.clamp(motion.x, -0.15000000596046448D, 0.15000000596046448D);
            double d1 = Mth.clamp(motion.z, -0.15000000596046448D, 0.15000000596046448D);
            double d2 = Math.max(motion.y, -0.15000000596046448D);

            if (d2 < 0.0D && !this.getFeetBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof net.minecraft.world.entity.player.Player) {
                d2 = 0.0D;
            }

            motion = new Vec3(d0, d2, d1);
        }

        return motion;
    }

    private float getFrictionInfluencedSpeed(float slipperiness) {
        return this.onGround ? this.getSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness)) : this.flyingSpeed;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float movementSpeed) {
        this.speed = movementSpeed;
    }

    public boolean doHurtTarget(Entity target) {
        this.setLastHurtMob(target);
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level.isClientSide) {
            int i = this.getArrowCount();

            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                --this.removeArrowTime;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();

            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                --this.removeStingerTime;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdates();
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d0 = this.getX() - this.xo;
        double d1 = this.getZ() - this.zo;
        float f = (float) (d0 * d0 + d1 * d1);
        float f1 = this.yBodyRot;
        float f2 = 0.0F;

        this.oRun = this.run;
        float f3 = 0.0F;

        if (f > 0.0025000002F) {
            f3 = 1.0F;
            f2 = (float) Math.sqrt((double) f) * 3.0F;
            float f4 = (float) Mth.atan2(d1, d0) * 57.295776F - 90.0F;
            float f5 = Mth.abs(Mth.wrapDegrees(this.getYRot()) - f4);

            if (95.0F < f5 && f5 < 265.0F) {
                f1 = f4 - 180.0F;
            } else {
                f1 = f4;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.getYRot();
        }

        if (!this.onGround) {
            f3 = 0.0F;
        }

        this.run += (f3 - this.run) * 0.3F;
        this.level.getProfiler().push("headTurn");
        f2 = this.tickHeadTurn(f1, f2);
        this.level.getProfiler().pop();
        this.level.getProfiler().push("rangeChecks");

        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        this.level.getProfiler().pop();
        this.animStep += f2;
        if (this.isFallFlying()) {
            ++this.fallFlyTicks;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }
    }

    public void detectEquipmentUpdates() {
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();

        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }

    }

    @Nullable
    private Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];
            ItemStack itemstack;

            switch (enumitemslot.getType()) {
                case HAND:
                    itemstack = this.getLastHandItem(enumitemslot);
                    break;
                case ARMOR:
                    itemstack = this.getLastArmorItem(enumitemslot);
                    break;
                default:
                    continue;
            }

            ItemStack itemstack1 = this.getItemBySlot(enumitemslot);

            if (this.equipmentHasChanged(itemstack, itemstack1)) {
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(enumitemslot, itemstack1);
                if (!itemstack.isEmpty()) {
                    this.getAttributes().removeAttributeModifiers(itemstack.getAttributeModifiers(enumitemslot));
                }

                if (!itemstack1.isEmpty()) {
                    this.getAttributes().addTransientAttributeModifiers(itemstack1.getAttributeModifiers(enumitemslot));
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack stack, ItemStack stack2) {
        return !ItemStack.matches(stack2, stack);
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> equipmentChanges) {
        ItemStack itemstack = (ItemStack) equipmentChanges.get(EquipmentSlot.MAINHAND);
        ItemStack itemstack1 = (ItemStack) equipmentChanges.get(EquipmentSlot.OFFHAND);

        if (itemstack != null && itemstack1 != null && ItemStack.matches(itemstack, this.getLastHandItem(EquipmentSlot.OFFHAND)) && ItemStack.matches(itemstack1, this.getLastHandItem(EquipmentSlot.MAINHAND))) {
            ((ServerLevel) this.level).getChunkSource().broadcast(this, new ClientboundEntityEventPacket(this, (byte) 55));
            equipmentChanges.remove(EquipmentSlot.MAINHAND);
            equipmentChanges.remove(EquipmentSlot.OFFHAND);
            this.setLastHandItem(EquipmentSlot.MAINHAND, itemstack.copy());
            this.setLastHandItem(EquipmentSlot.OFFHAND, itemstack1.copy());
        }

    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> equipmentChanges) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(equipmentChanges.size());

        equipmentChanges.forEach((enumitemslot, itemstack) -> {
            ItemStack itemstack1 = itemstack.copy();

            list.add(Pair.of(enumitemslot, itemstack1));
            switch (enumitemslot.getType()) {
                case HAND:
                    this.setLastHandItem(enumitemslot, itemstack1);
                    break;
                case ARMOR:
                    this.setLastArmorItem(enumitemslot, itemstack1);
            }

        });
        ((ServerLevel) this.level).getChunkSource().broadcast(this, new ClientboundSetEquipmentPacket(this.getId(), list));
    }

    private ItemStack getLastArmorItem(EquipmentSlot slot) {
        return (ItemStack) this.lastArmorItemStacks.get(slot.getIndex());
    }

    private void setLastArmorItem(EquipmentSlot slot, ItemStack armor) {
        this.lastArmorItemStacks.set(slot.getIndex(), armor);
    }

    private ItemStack getLastHandItem(EquipmentSlot slot) {
        return (ItemStack) this.lastHandItemStacks.get(slot.getIndex());
    }

    private void setLastHandItem(EquipmentSlot slot, ItemStack stack) {
        this.lastHandItemStacks.set(slot.getIndex(), stack);
    }

    protected float tickHeadTurn(float bodyRotation, float headRotation) {
        float f2 = Mth.wrapDegrees(bodyRotation - this.yBodyRot);

        this.yBodyRot += f2 * 0.3F;
        float f3 = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
        boolean flag = f3 < -90.0F || f3 >= 90.0F;

        if (f3 < -75.0F) {
            f3 = -75.0F;
        }

        if (f3 >= 75.0F) {
            f3 = 75.0F;
        }

        this.yBodyRot = this.getYRot() - f3;
        if (f3 * f3 > 2500.0F) {
            this.yBodyRot += f3 * 0.2F;
        }

        if (flag) {
            headRotation *= -1.0F;
        }

        return headRotation;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            --this.noJumpDelay;
        }

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            double d0 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
            double d1 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
            double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
            double d3 = Mth.wrapDegrees(this.lerpYRot - (double) this.getYRot());

            this.setYRot(this.getYRot() + (float) d3 / (float) this.lerpSteps);
            this.setXRot(this.getXRot() + (float) (this.lerpXRot - (double) this.getXRot()) / (float) this.lerpSteps);
            --this.lerpSteps;
            this.setPos(d0, d1, d2);
            this.setRot(this.getYRot(), this.getXRot());
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }

        if (this.lerpHeadSteps > 0) {
            this.yHeadRot += (float) Mth.wrapDegrees(this.lyHeadRot - (double) this.yHeadRot) / (float) this.lerpHeadSteps;
            --this.lerpHeadSteps;
        }

        Vec3 vec3d = this.getDeltaMovement();
        double d4 = vec3d.x;
        double d5 = vec3d.y;
        double d6 = vec3d.z;

        if (Math.abs(vec3d.x) < 0.003D) {
            d4 = 0.0D;
        }

        if (Math.abs(vec3d.y) < 0.003D) {
            d5 = 0.0D;
        }

        if (Math.abs(vec3d.z) < 0.003D) {
            d6 = 0.0D;
        }

        this.setDeltaMovement(d4, d5, d6);
        this.level.getProfiler().push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            this.level.getProfiler().push("newAi");
            this.serverAiStep();
            this.level.getProfiler().pop();
        }

        this.level.getProfiler().pop();
        this.level.getProfiler().push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double d7;

            if (this.isInLava()) {
                d7 = this.getFluidHeight(FluidTags.LAVA);
            } else {
                d7 = this.getFluidHeight(FluidTags.WATER);
            }

            boolean flag = this.isInWater() && d7 > 0.0D;
            double d8 = this.getFluidJumpThreshold();

            if (flag && (!this.onGround || d7 > d8)) {
                this.jumpInLiquid(FluidTags.WATER);
            } else if (this.isInLava() && (!this.onGround || d7 > d8)) {
                this.jumpInLiquid(FluidTags.LAVA);
            } else if ((this.onGround || flag && d7 <= d8) && this.noJumpDelay == 0) {
                this.jumpFromGround();
                this.noJumpDelay = 10;
            }
        } else {
            this.noJumpDelay = 0;
        }

        this.level.getProfiler().pop();
        this.level.getProfiler().push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        this.updateFallFlying();
        AABB axisalignedbb = this.getBoundingBox();

        // SpigotTimings.timerEntityAIMove.startTiming(); // Spigot // Paper
        this.travel(new Vec3((double) this.xxa, (double) this.yya, (double) this.zza));
        // SpigotTimings.timerEntityAIMove.stopTiming(); // Spigot // Paper
        this.level.getProfiler().pop();
        this.level.getProfiler().push("freezing");
        boolean flag1 = this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES);
        int i;

        if (!this.level.isClientSide && !this.isDeadOrDying()) {
            i = this.getTicksFrozen();
            if (this.isInPowderSnow && this.canFreeze()) {
                this.setTicksFrozen(Math.min(this.getTicksRequiredToFreeze(), i + 1));
            } else {
                this.setTicksFrozen(Math.max(0, i - 2));
            }
        }

        this.removeFrost();
        this.tryAddFrost();
        if (!this.level.isClientSide && this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
            i = flag1 ? 5 : 1;
            this.hurt(DamageSource.FREEZE, (float) i);
        }

        this.level.getProfiler().pop();
        this.level.getProfiler().push("push");
        if (this.autoSpinAttackTicks > 0) {
            --this.autoSpinAttackTicks;
            this.checkAutoSpinAttack(axisalignedbb, this.getBoundingBox());
        }

        this.pushEntities();
        this.level.getProfiler().pop();
        if (!this.level.isClientSide && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurt(DamageSource.DROWN, 1.0F);
        }

    }

    public boolean isSensitiveToWater() {
        return false;
    }

    private void updateFallFlying() {
        boolean flag = this.getSharedFlag(7);

        if (flag && !this.onGround && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);

            if (itemstack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemstack)) {
                flag = true;
                int i = this.fallFlyTicks + 1;

                if (!this.level.isClientSide && i % 10 == 0) {
                    int j = i / 10;

                    if (j % 2 == 0) {
                        itemstack.hurtAndBreak(1, this, (entityliving) -> {
                            entityliving.broadcastBreakEvent(EquipmentSlot.CHEST);
                        });
                    }

                    this.gameEvent(GameEvent.ELYTRA_GLIDE);
                }
            } else {
                flag = false;
            }
        } else {
            flag = false;
        }

        if (!this.level.isClientSide) {
            if (flag != this.getSharedFlag(7) && !CraftEventFactory.callToggleGlideEvent(this, flag).isCancelled()) // CraftBukkit
            this.setSharedFlag(7, flag);
        }

    }

    protected void serverAiStep() {}

    protected void pushEntities() {
        if (this.level.isClientSide()) {
            this.level.getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.player.Player.class), this.getBoundingBox(), EntitySelector.pushableBy(this)).forEach(this::doPush);
        } else {
            List<Entity> list = this.level.getEntities((Entity) this, this.getBoundingBox(), EntitySelector.pushableBy(this));

            if (!list.isEmpty()) {
                int i = this.level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
                int j;

                if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                    j = 0;

                    for (int k = 0; k < list.size(); ++k) {
                        if (!((Entity) list.get(k)).isPassenger()) {
                            ++j;
                        }
                    }

                    if (j > i - 1) {
                        this.hurt(DamageSource.CRAMMING, 6.0F);
                    }
                }

                for (j = 0; j < list.size(); ++j) {
                    Entity entity = (Entity) list.get(j);

                    this.doPush(entity);
                }
            }

        }
    }

    protected void checkAutoSpinAttack(AABB a, AABB b) {
        AABB axisalignedbb2 = a.minmax(b);
        List<Entity> list = this.level.getEntities(this, axisalignedbb2);

        if (!list.isEmpty()) {
            for (int i = 0; i < list.size(); ++i) {
                Entity entity = (Entity) list.get(i);

                if (entity instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity) entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2D));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level.isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
        }

    }

    protected void doPush(Entity entity) {
        entity.push(this);
    }

    protected void doAutoAttackOnTouch(LivingEntity target) {}

    public boolean isAutoSpinAttack() {
        return ((Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();

        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level.isClientSide) {
            this.dismountVehicle(entity);
        }

    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.resetFallDistance();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = (double) yaw;
        this.lerpXRot = (double) pitch;
        this.lerpSteps = interpolationSteps;
    }

    @Override
    public void lerpHeadTo(float yaw, int interpolationSteps) {
        this.lyHeadRot = (double) yaw;
        this.lerpHeadSteps = interpolationSteps;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public void onItemPickup(ItemEntity item) {
        net.minecraft.world.entity.player.Player entityhuman = item.getThrower() != null ? this.level.getPlayerByUUID(item.getThrower()) : null;

        if (entityhuman instanceof ServerPlayer) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer) entityhuman, item.getItem(), this);
        }

    }

    public void take(Entity item, int count) {
        if (!item.isRemoved() && !this.level.isClientSide && (item instanceof ItemEntity || item instanceof AbstractArrow || item instanceof ExperienceOrb)) {
            ((ServerLevel) this.level).getChunkSource().broadcast(item, new ClientboundTakeItemEntityPacket(item.getId(), this.getId(), count));
        }

    }

    public boolean hasLineOfSight(Entity entity) {
        if (entity.level != this.level) {
            return false;
        } else {
            Vec3 vec3d = new Vec3(this.getX(), this.getEyeY(), this.getZ());
            Vec3 vec3d1 = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());

            return vec3d1.distanceTo(vec3d) > 128.0D ? false : this.level.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        }
    }

    @Override
    public float getViewYRot(float tickDelta) {
        return tickDelta == 1.0F ? this.yHeadRot : Mth.lerp(tickDelta, this.yHeadRotO, this.yHeadRot);
    }

    public float getAttackAnim(float tickDelta) {
        float f1 = this.attackAnim - this.oAttackAnim;

        if (f1 < 0.0F) {
            ++f1;
        }

        return this.oAttackAnim + f1 * tickDelta;
    }

    public boolean isEffectiveAi() {
        return !this.level.isClientSide;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved() && this.collides; // CraftBukkit
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.isSpectator() && !this.onClimbable() && this.collides; // CraftBukkit
    }

    // CraftBukkit start - collidable API
    @Override
    public boolean canCollideWithBukkit(Entity entity) {
        return this.isPushable() && this.collides != this.collidableExemptions.contains(entity.getUUID());
    }
    // CraftBukkit end

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    @Override
    public void setYHeadRot(float headYaw) {
        this.yHeadRot = headYaw;
    }

    @Override
    public void setYBodyRot(float bodyYaw) {
        this.yBodyRot = bodyYaw;
    }

    @Override
    protected Vec3 getRelativePortalPosition(Direction.Axis portalAxis, BlockUtil.FoundRectangle portalRect) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(portalAxis, portalRect));
    }

    public static Vec3 resetForwardDirectionOfRelativePortalPosition(Vec3 pos) {
        return new Vec3(pos.x, pos.y, 0.0D);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public void setAbsorptionAmount(float amount) {
        if (amount < 0.0F || Float.isNaN(amount)) { // Paper
            amount = 0.0F;
        }

        this.absorptionAmount = amount;
    }

    public void onEnterCombat() {}

    public void onLeaveCombat() {}

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return ((Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return ((Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSame(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }

    }

    protected void updateUsingItem(ItemStack stack) {
        stack.onUseTick(this.level, this, this.getUseItemRemainingTicks());
        if (this.shouldTriggerItemUseEffects()) {
            this.triggerItemUseEffects(stack, 5);
        }

        if (--this.useItemRemaining == 0 && !this.level.isClientSide && !stack.useOnRelease()) {
            this.completeUsingItem();
        }

    }

    private boolean shouldTriggerItemUseEffects() {
        int i = this.getUseItemRemainingTicks();
        FoodProperties foodinfo = this.useItem.getItem().getFoodProperties();
        boolean flag = foodinfo != null && foodinfo.isFastFood();

        flag |= i <= this.useItem.getUseDuration() - 7;
        return flag && i % 4 == 0;
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }

    }

    protected void setLivingEntityFlag(int mask, boolean value) {
        byte b0 = (Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS);
        int j;

        if (value) {
            j = b0 | mask;
        } else {
            j = b0 & ~mask;
        }

        this.entityData.set(LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) j);
    }

    public void startUsingItem(InteractionHand hand) {
        ItemStack itemstack = this.getItemInHand(hand);

        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            this.useItem = itemstack;
            this.useItemRemaining = itemstack.getUseDuration();
            if (!this.level.isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
                this.gameEvent(GameEvent.ITEM_INTERACT_START);
            }

        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (LivingEntity.SLEEPING_POS_ID.equals(data)) {
            if (this.level.isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (LivingEntity.DATA_LIVING_ENTITY_FLAGS.equals(data) && this.level.isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration();
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Vec3 target) {
        super.lookAt(anchorPoint, target);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    protected void triggerItemUseEffects(ItemStack stack, int particleCount) {
        if (!stack.isEmpty() && this.isUsingItem()) {
            if (stack.getUseAnimation() == UseAnim.DRINK) {
                this.playSound(this.getDrinkingSound(stack), 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }

            if (stack.getUseAnimation() == UseAnim.EAT) {
                this.spawnItemParticles(stack, particleCount);
                this.playSound(this.getEatingSound(stack), 0.5F + 0.5F * (float) this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

        }
    }

    private void spawnItemParticles(ItemStack stack, int count) {
        for (int j = 0; j < count; ++j) {
            Vec3 vec3d = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);

            vec3d = vec3d.xRot(-this.getXRot() * 0.017453292F);
            vec3d = vec3d.yRot(-this.getYRot() * 0.017453292F);
            double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
            Vec3 vec3d1 = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.3D, d0, 0.6D);

            vec3d1 = vec3d1.xRot(-this.getXRot() * 0.017453292F);
            vec3d1 = vec3d1.yRot(-this.getYRot() * 0.017453292F);
            vec3d1 = vec3d1.add(this.getX(), this.getEyeY(), this.getZ());
            this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
        }

    }

    protected void completeUsingItem() {
        if (!this.level.isClientSide || this.isUsingItem()) {
            InteractionHand enumhand = this.getUsedItemHand();

            if (!this.useItem.equals(this.getItemInHand(enumhand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    this.triggerItemUseEffects(this.useItem, 16);
                    // CraftBukkit start - fire PlayerItemConsumeEvent
                    ItemStack itemstack;
                    PlayerItemConsumeEvent event = null; // Paper
                    if (this instanceof ServerPlayer) {
                        org.bukkit.inventory.ItemStack craftItem = CraftItemStack.asBukkitCopy(this.useItem);
                        org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(enumhand);
                        event = new PlayerItemConsumeEvent((Player) this.getBukkitEntity(), craftItem, hand); // Paper
                        level.getCraftServer().getPluginManager().callEvent(event);

                        if (event.isCancelled()) {
                            // Update client
                            ((ServerPlayer) this).getBukkitEntity().updateInventory();
                            ((ServerPlayer) this).getBukkitEntity().updateScaledHealth();
                            return;
                        }

                        itemstack = (craftItem.equals(event.getItem())) ? this.useItem.finishUsingItem(this.level, this) : CraftItemStack.asNMSCopy(event.getItem()).finishUsingItem(level, this);
                    } else {
                        itemstack = this.useItem.finishUsingItem(this.level, this);
                    }

                // Paper start - save the default replacement item and change it if necessary
                final ItemStack defaultReplacement = itemstack;
                if (event != null && event.getReplacement() != null) {
                    itemstack = CraftItemStack.asNMSCopy(event.getReplacement());
                }
                // Paper end
                    // CraftBukkit end

                    if (itemstack != this.useItem) {
                        this.setItemInHand(enumhand, itemstack);
                    }

                    this.stopUsingItem();
                // Paper start - if the replacement is anything but the default, update the client inventory
                if (this instanceof ServerPlayer && !com.google.common.base.Objects.equal(defaultReplacement, itemstack)) {
                    ((ServerPlayer) this).getBukkitEntity().updateInventory();
                }
                // Paper end
                }

            }
        }
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration() - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            this.useItem.releaseUsing(this.level, this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level.isClientSide) {
            boolean flag = this.isUsingItem();

            this.setLivingEntityFlag(1, false);
            if (flag) {
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item item = this.useItem.getItem();

            return item.getUseAnimation(this.useItem) != UseAnim.BLOCK ? false : item.getUseDuration(this.useItem) - this.useItemRemaining >= 5;
        } else {
            return false;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(Pose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double x, double y, double z, boolean particleEffects) {
        // CraftBukkit start
        return this.randomTeleport(x, y, z, particleEffects, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN).orElse(false);
    }

    public Optional<Boolean> randomTeleport(double d0, double d1, double d2, boolean flag, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        double d3 = this.getX();
        double d4 = this.getY();
        double d5 = this.getZ();
        double d6 = d1;
        boolean flag1 = false;
        BlockPos blockposition = new BlockPos(d0, d1, d2);
        Level world = this.level;

        if (world.hasChunkAt(blockposition)) {
            boolean flag2 = false;

            while (!flag2 && blockposition.getY() > world.getMinBuildHeight()) {
                BlockPos blockposition1 = blockposition.below();
                BlockState iblockdata = world.getBlockState(blockposition1);

                if (iblockdata.getMaterial().blocksMotion()) {
                    flag2 = true;
                } else {
                    --d6;
                    blockposition = blockposition1;
                }
            }

            if (flag2) {
                // CraftBukkit start - Teleport event
                // this.teleportTo(d0, d6, d2);

                // first set position, to check if the place to teleport is valid
                this.setPos(d0, d6, d2);
                if (world.noCollision((Entity) this) && !world.containsAnyLiquid(this.getBoundingBox())) {
                    flag1 = true;
                }
                // now revert and call event if the teleport place is valid
                this.setPos(d3, d4, d5);

                if (flag1) {
                    if (!(this instanceof ServerPlayer)) {
                        EntityTeleportEvent teleport = new EntityTeleportEvent(this.getBukkitEntity(), new Location(this.level.getWorld(), d3, d4, d5), new Location(this.level.getWorld(), d0, d6, d2));
                        this.level.getCraftServer().getPluginManager().callEvent(teleport);
                        if (!teleport.isCancelled()) {
                            Location to = teleport.getTo();
                            this.teleportTo(to.getX(), to.getY(), to.getZ());
                        } else {
                            return Optional.empty();
                        }
                    } else {
                        // player teleport event is called in the underlining code
                        if (((ServerPlayer) this).connection.teleport(d0, d6, d2, this.getYRot(), this.getXRot(), java.util.Collections.emptySet(), false, cause)) {
                            return Optional.empty();
                        }
                    }
                }
                // CraftBukkit end
            }
        }

        if (!flag1) {
            // this.enderTeleportTo(d3, d4, d5); // CraftBukkit - already set the location back
            return Optional.of(false); // CraftBukkit
        } else {
            if (flag) {
                world.broadcastEntityEvent(this, (byte) 46);
            }

            if (this instanceof PathfinderMob) {
                ((PathfinderMob) this).getNavigation().stop();
            }

            return Optional.of(true); // CraftBukkit
        }
    }

    public boolean isAffectedByPotions() {
        return true;
    }

    public boolean attackable() {
        return true;
    }

    public void setRecordPlayingNearby(BlockPos songPosition, boolean playing) {}

    public boolean canTakeItem(ItemStack stack) {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SLEEPING ? LivingEntity.SLEEPING_DIMENSIONS : super.getDimensions(pose).scale(this.getScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose pose) {
        EntityDimensions entitysize = this.getDimensions(pose);

        return new AABB((double) (-entitysize.width / 2.0F), 0.0D, (double) (-entitysize.width / 2.0F), (double) (entitysize.width / 2.0F), (double) entitysize.height, (double) (entitysize.width / 2.0F));
    }

    public Optional<BlockPos> getSleepingPos() {
        return (Optional) this.entityData.get(LivingEntity.SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos pos) {
        this.entityData.set(LivingEntity.SLEEPING_POS_ID, Optional.of(pos));
    }

    public void clearSleepingPos() {
        this.entityData.set(LivingEntity.SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos pos) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState iblockdata = this.level.getBlockState(pos);

        if (iblockdata.getBlock() instanceof BedBlock) {
            this.level.setBlock(pos, (BlockState) iblockdata.setValue(BedBlock.OCCUPIED, true), 3);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(pos);
        this.setSleepingPos(pos);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPos pos) {
        this.setPos((double) pos.getX() + 0.5D, (double) pos.getY() + 0.6875D, (double) pos.getZ() + 0.5D);
    }

    private boolean checkBedExists() {
        return (Boolean) this.getSleepingPos().map((blockposition) -> {
            return this.level.getBlockState(blockposition).getBlock() instanceof BedBlock;
        }).orElse(false);
    }

    public void stopSleeping() {
        Optional<BlockPos> optional = this.getSleepingPos(); // CraftBukkit - decompile error
        Level world = this.level;

        java.util.Objects.requireNonNull(this.level);
        optional.filter(world::hasChunkAt).ifPresent((blockposition) -> {
            BlockState iblockdata = this.level.getBlockState(blockposition);

            if (iblockdata.getBlock() instanceof BedBlock) {
                Direction enumdirection = (Direction) iblockdata.getValue(BedBlock.FACING);

                this.level.setBlock(blockposition, (BlockState) iblockdata.setValue(BedBlock.OCCUPIED, false), 3);
                Vec3 vec3d = (Vec3) BedBlock.findStandUpPosition(this.getType(), this.level, blockposition, enumdirection, this.getYRot()).orElseGet(() -> {
                    BlockPos blockposition1 = blockposition.above();

                    return new Vec3((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.1D, (double) blockposition1.getZ() + 0.5D);
                });
                Vec3 vec3d1 = Vec3.atBottomCenterOf(blockposition).subtract(vec3d).normalize();
                float f = (float) Mth.wrapDegrees(Mth.atan2(vec3d1.z, vec3d1.x) * 57.2957763671875D - 90.0D);

                this.setPos(vec3d.x, vec3d.y, vec3d.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }

        });
        Vec3 vec3d = this.position();

        this.setPose(Pose.STANDING);
        this.setPos(vec3d.x, vec3d.y, vec3d.z);
        this.clearSleepingPos();
    }

    @Nullable
    public Direction getBedOrientation() {
        BlockPos blockposition = (BlockPos) this.getSleepingPos().orElse(null); // CraftBukkit - decompile error

        return blockposition != null ? BedBlock.getBedOrientation(this.level, blockposition) : null;
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    @Override
    protected final float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return pose == Pose.SLEEPING ? 0.2F : this.getStandingEyeHeight(pose, dimensions);
    }

    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return super.getEyeHeight(pose, dimensions);
    }

    public ItemStack getProjectile(ItemStack stack) {
        return ItemStack.EMPTY;
    }

    public ItemStack eat(Level world, ItemStack stack) {
        if (stack.isEdible()) {
            world.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), this.getEatingSound(stack), SoundSource.NEUTRAL, 1.0F, 1.0F + (world.random.nextFloat() - world.random.nextFloat()) * 0.4F);
            this.addEatEffect(stack, world, this);
            if (!(this instanceof net.minecraft.world.entity.player.Player) || !((net.minecraft.world.entity.player.Player) this).getAbilities().instabuild) {
                stack.shrink(1);
            }

            this.gameEvent(GameEvent.EAT);
        }

        return stack;
    }

    private void addEatEffect(ItemStack stack, Level world, LivingEntity targetEntity) {
        Item item = stack.getItem();

        if (item.isEdible()) {
            List<Pair<MobEffectInstance, Float>> list = item.getFoodProperties().getEffects();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Pair<MobEffectInstance, Float> pair = (Pair) iterator.next();

                if (!world.isClientSide && pair.getFirst() != null && world.random.nextFloat() < (Float) pair.getSecond()) {
                    targetEntity.addEffect(new MobEffectInstance((MobEffectInstance) pair.getFirst()), EntityPotionEffectEvent.Cause.FOOD); // CraftBukkit
                }
            }
        }

    }

    public static byte entityEventForEquipmentBreak(EquipmentSlot slot) {
        switch (slot) {
            case MAINHAND:
                return 47;
            case OFFHAND:
                return 48;
            case HEAD:
                return 49;
            case CHEST:
                return 50;
            case FEET:
                return 52;
            case LEGS:
                return 51;
            default:
                return 47;
        }
    }

    public void broadcastBreakEvent(EquipmentSlot slot) {
        this.level.broadcastEntityEvent(this, LivingEntity.entityEventForEquipmentBreak(slot));
    }

    public void broadcastBreakEvent(InteractionHand hand) {
        this.broadcastBreakEvent(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (this.getItemBySlot(EquipmentSlot.HEAD).is(Items.DRAGON_HEAD)) {
            float f = 0.5F;

            return this.getBoundingBox().inflate(0.5D, 0.5D, 0.5D);
        } else {
            return super.getBoundingBoxForCulling();
        }
    }

    public static EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        Item item = stack.getItem();

        return !stack.is(Items.CARVED_PUMPKIN) && (!(item instanceof BlockItem) || !(((BlockItem) item).getBlock() instanceof AbstractSkullBlock)) ? (item instanceof ArmorItem ? ((ArmorItem) item).getSlot() : (stack.is(Items.ELYTRA) ? EquipmentSlot.CHEST : (stack.is(Items.SHIELD) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND))) : EquipmentSlot.HEAD;
    }

    private static SlotAccess createEquipmentSlotAccess(LivingEntity entity, EquipmentSlot slot) {
        return slot != EquipmentSlot.HEAD && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND ? SlotAccess.forEquipmentSlot(entity, slot, (itemstack) -> {
            return itemstack.isEmpty() || Mob.getEquipmentSlotForItem(itemstack) == slot;
        }) : SlotAccess.forEquipmentSlot(entity, slot);
    }

    @Nullable
    private static EquipmentSlot getEquipmentSlot(int slotId) {
        return slotId == 100 + EquipmentSlot.HEAD.getIndex() ? EquipmentSlot.HEAD : (slotId == 100 + EquipmentSlot.CHEST.getIndex() ? EquipmentSlot.CHEST : (slotId == 100 + EquipmentSlot.LEGS.getIndex() ? EquipmentSlot.LEGS : (slotId == 100 + EquipmentSlot.FEET.getIndex() ? EquipmentSlot.FEET : (slotId == 98 ? EquipmentSlot.MAINHAND : (slotId == 99 ? EquipmentSlot.OFFHAND : null)))));
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        EquipmentSlot enumitemslot = LivingEntity.getEquipmentSlot(mappedIndex);

        return enumitemslot != null ? LivingEntity.createEquipmentSlotAccess(this, enumitemslot) : super.getSlot(mappedIndex);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        } else {
            boolean flag = !this.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.FREEZE_IMMUNE_WEARABLES) && !this.getItemBySlot(EquipmentSlot.CHEST).is(ItemTags.FREEZE_IMMUNE_WEARABLES) && !this.getItemBySlot(EquipmentSlot.LEGS).is(ItemTags.FREEZE_IMMUNE_WEARABLES) && !this.getItemBySlot(EquipmentSlot.FEET).is(ItemTags.FREEZE_IMMUNE_WEARABLES);

            return flag && super.canFreeze();
        }
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level.isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        float f = packet.getYRot();
        float f1 = packet.getXRot();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.yBodyRot = packet.getYHeadRot();
        this.yHeadRot = packet.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(packet.getId());
        this.setUUID(packet.getUUID());
        this.absMoveTo(d0, d1, d2, f, f1);
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
    }

    public boolean canDisableShield() {
        return this.getMainHandItem().getItem() instanceof AxeItem;
    }

    public static record Fallsounds(SoundEvent small, SoundEvent big) {

    }
}
