package net.minecraft.world.entity.animal.camel;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Dynamic;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RiderShieldingMount;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Camel extends AbstractHorse implements PlayerRideableJumping, RiderShieldingMount, Saddleable {
    public static final Ingredient TEMPTATION_ITEM = Ingredient.of(Items.CACTUS);
    public static final int DASH_COOLDOWN_TICKS = 55;
    private static final float RUNNING_SPEED_BONUS = 0.1F;
    private static final float DASH_VERTICAL_MOMENTUM = 1.4285F;
    private static final float DASH_HORIZONTAL_MOMENTUM = 22.2222F;
    private static final int SITDOWN_DURATION_TICKS = 40;
    private static final int STANDUP_DURATION_TICKS = 52;
    private static final int IDLE_MINIMAL_DURATION_TICKS = 80;
    private static final float SITTING_HEIGHT_DIFFERENCE = 1.43F;
    public static final EntityDataAccessor<Boolean> DASH = SynchedEntityData.defineId(Camel.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Long> LAST_POSE_CHANGE_TICK = SynchedEntityData.defineId(Camel.class, EntityDataSerializers.LONG);
    public final AnimationState walkAnimationState = new AnimationState();
    public final AnimationState sitAnimationState = new AnimationState();
    public final AnimationState sitPoseAnimationState = new AnimationState();
    public final AnimationState sitUpAnimationState = new AnimationState();
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState dashAnimationState = new AnimationState();
    private static final EntityDimensions SITTING_DIMENSIONS = EntityDimensions.scalable(EntityType.CAMEL.getWidth(), EntityType.CAMEL.getHeight() - 1.43F);
    private int dashCooldown = 0;
    private int idleAnimationTimeout = 0;

    public Camel(EntityType<? extends Camel> type, Level world) {
        super(type, world);
        this.maxUpStep = 1.5F;
        GroundPathNavigation groundPathNavigation = (GroundPathNavigation)this.getNavigation();
        groundPathNavigation.setCanFloat(true);
        groundPathNavigation.setCanWalkOverFences(true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("IsSitting", this.getPose() == Pose.SITTING);
        nbt.putLong("LastPoseTick", this.entityData.get(LAST_POSE_CHANGE_TICK));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.getBoolean("IsSitting")) {
            this.setPose(Pose.SITTING);
        }

        this.resetLastPoseChangeTick(nbt.getLong("LastPoseTick"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 32.0D).add(Attributes.MOVEMENT_SPEED, (double)0.09F).add(Attributes.JUMP_STRENGTH, (double)0.42F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DASH, false);
        this.entityData.define(LAST_POSE_CHANGE_TICK, -52L);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        CamelAi.initMemories(this, world.getRandom());
        this.entityData.set(LAST_POSE_CHANGE_TICK, world.getLevel().getGameTime() - 52L);
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    protected Brain.Provider<Camel> brainProvider() {
        return CamelAi.brainProvider();
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return CamelAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SITTING ? SITTING_DIMENSIONS.scale(this.getScale()) : super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height - 0.1F;
    }

    @Override
    public double getRiderShieldingHeight() {
        return 0.5D;
    }

    @Override
    protected void customServerAiStep() {
        this.level.getProfiler().push("camelBrain");
        Brain<Camel> brain = (Brain<Camel>) this.getBrain(); // Paper - decompile fix
        brain.tick((ServerLevel)this.level, this);
        this.level.getProfiler().pop();
        this.level.getProfiler().push("camelActivityUpdate");
        CamelAi.updateActivity(this);
        this.level.getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isDashing() && this.dashCooldown < 55 && (this.onGround || this.isInWater())) {
            this.setDashing(false);
        }

        if (this.dashCooldown > 0) {
            --this.dashCooldown;
            if (this.dashCooldown == 0) {
                this.level.playSound((Player)null, this.blockPosition(), SoundEvents.CAMEL_DASH_READY, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        if (this.level.isClientSide()) {
            this.setupAnimationStates();
        }

    }

    private void setupAnimationStates() {
        if (this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.tickCount);
        } else {
            --this.idleAnimationTimeout;
        }

        switch (this.getPose()) {
            case STANDING:
                this.sitAnimationState.stop();
                this.sitPoseAnimationState.stop();
                this.dashAnimationState.animateWhen(this.isDashing(), this.tickCount);
                this.sitUpAnimationState.animateWhen(this.isInPoseTransition(), this.tickCount);
                this.walkAnimationState.animateWhen((this.onGround || this.hasControllingPassenger()) && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D, this.tickCount);
                break;
            case SITTING:
                this.walkAnimationState.stop();
                this.sitUpAnimationState.stop();
                this.dashAnimationState.stop();
                if (this.isSittingDown()) {
                    this.sitAnimationState.startIfStopped(this.tickCount);
                    this.sitPoseAnimationState.stop();
                } else {
                    this.sitAnimationState.stop();
                    this.sitPoseAnimationState.startIfStopped(this.tickCount);
                }
                break;
            default:
                this.walkAnimationState.stop();
                this.sitAnimationState.stop();
                this.sitPoseAnimationState.stop();
                this.sitUpAnimationState.stop();
                this.dashAnimationState.stop();
        }

    }

    @Override
    public void travel(Vec3 movementInput) {
        if (this.isAlive()) {
            if (this.refuseToMove() && this.isOnGround()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
                movementInput = movementInput.multiply(0.0D, 1.0D, 0.0D);
            }

            super.travel(movementInput);
        }
    }

    public boolean refuseToMove() {
        return this.isPoseSitting() || this.isInPoseTransition();
    }

    @Override
    protected float getDrivenMovementSpeed(LivingEntity passenger) {
        float f = passenger.isSprinting() && this.getJumpCooldown() == 0 ? 0.1F : 0.0F;
        return (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED) + f;
    }

    @Override
    protected boolean mountIgnoresControllerInput(LivingEntity passenger) {
        boolean bl = this.isInPoseTransition();
        if (this.isPoseSitting() && !bl && passenger.zza > 0.0F) {
            this.standUp();
        }

        return this.refuseToMove() || super.mountIgnoresControllerInput(passenger);
    }

    @Override
    public boolean canJump(Player player) {
        return !this.refuseToMove() && this.getControllingPassenger() == player && super.canJump(player);
    }

    @Override
    public void onPlayerJump(int strength) {
        if (this.isSaddled() && this.dashCooldown <= 0 && this.isOnGround()) {
            super.onPlayerJump(strength);
        }
    }

    @Override
    protected void executeRidersJump(float strength, float sidewaysSpeed, float forwardSpeed) {
        double d = this.getAttributeValue(Attributes.JUMP_STRENGTH) * (double)this.getBlockJumpFactor() + this.getJumpBoostPower();
        this.addDeltaMovement(this.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale((double)(22.2222F * strength) * this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (double)this.getBlockSpeedFactor()).add(0.0D, (double)(1.4285F * strength) * d, 0.0D));
        this.dashCooldown = 55;
        this.setDashing(true);
        this.hasImpulse = true;
    }

    public boolean isDashing() {
        return this.entityData.get(DASH);
    }

    public void setDashing(boolean dashing) {
        this.entityData.set(DASH, dashing);
    }

    public boolean isPanicking() {
        return this.getBrain().checkMemory(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_PRESENT);
    }

    @Override
    public void handleStartJump(int height) {
        this.playSound(SoundEvents.CAMEL_DASH, 1.0F, 1.0F);
        this.setDashing(true);
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public int getJumpCooldown() {
        return this.dashCooldown;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAMEL_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.CAMEL_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAMEL_HURT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.getSoundType() == SoundType.SAND) {
            this.playSound(SoundEvents.CAMEL_STEP_SAND, 1.0F, 1.0F);
        } else {
            this.playSound(SoundEvents.CAMEL_STEP, 1.0F, 1.0F);
        }

    }

    @Override
    public boolean isFood(ItemStack stack) {
        return TEMPTATION_ITEM.test(stack);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            InteractionResult interactionResult = itemStack.interactLivingEntity(player, this, hand);
            if (interactionResult.consumesAction()) {
                return interactionResult;
            } else if (this.isFood(itemStack)) {
                return this.fedFood(player, itemStack);
            } else {
                if (this.getPassengers().size() < 2 && !this.isBaby()) {
                    this.doPlayerRide(player);
                }

                return InteractionResult.sidedSuccess(this.level.isClientSide);
            }
        }
    }

    @Override
    protected void onLeashDistance(float leashLength) {
        if (leashLength > 6.0F && this.isPoseSitting() && !this.isInPoseTransition()) {
            this.standUp();
        }

    }

    @Override
    protected boolean handleEating(Player player, ItemStack item) {
        if (!this.isFood(item)) {
            return false;
        } else {
            boolean bl = this.getHealth() < this.getMaxHealth();
            if (bl) {
                this.heal(2.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper
            }

            boolean bl2 = this.isTamed() && this.getAge() == 0 && this.canFallInLove();
            if (bl2) {
                this.setInLove(player);
            }

            boolean bl3 = this.isBaby();
            if (bl3) {
                this.level.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
                if (!this.level.isClientSide) {
                    this.ageUp(10);
                }
            }

            if (!bl && !bl2 && !bl3) {
                return false;
            } else {
                if (!this.isSilent()) {
                    SoundEvent soundEvent = this.getEatingSound();
                    if (soundEvent != null) {
                        this.level.playSound((Player)null, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
                    }
                }

                return true;
            }
        }
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        if (other != this && other instanceof Camel camel) {
            if (this.canParent() && camel.canParent()) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public Camel getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return EntityType.CAMEL.create(serverLevel);
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.CAMEL_EAT;
    }

    @Override
    protected boolean damageEntity0(DamageSource source, float amount) { // Paper - fix CB method rename issue
        this.standUpPanic();
        return super.damageEntity0(source, amount); // Paper - fix CB method rename issue
    }

    @Override
    public void positionRider(Entity passenger) {
        int i = this.getPassengers().indexOf(passenger);
        if (i >= 0) {
            boolean bl = i == 0;
            float f = 0.5F;
            float g = (float)(this.isRemoved() ? (double)0.01F : this.getBodyAnchorAnimationYOffset(bl, 0.0F) + passenger.getMyRidingOffset());
            if (this.getPassengers().size() > 1) {
                if (!bl) {
                    f = -0.7F;
                }

                if (passenger instanceof Animal) {
                    f += 0.2F;
                }
            }

            Vec3 vec3 = (new Vec3(0.0D, 0.0D, (double)f)).yRot(-this.yBodyRot * ((float)Math.PI / 180F));
            passenger.setPos(this.getX() + vec3.x, this.getY() + (double)g, this.getZ() + vec3.z);
            this.clampRotation(passenger);
        }
    }

    private double getBodyAnchorAnimationYOffset(boolean bl, float f) {
        double d = this.getPassengersRidingOffset();
        float g = this.getScale() * 1.43F;
        float h = g - this.getScale() * 0.2F;
        float i = g - h;
        boolean bl2 = this.isInPoseTransition();
        boolean bl3 = this.getPose() == Pose.SITTING;
        if (bl2) {
            int j = bl3 ? 40 : 52;
            int k;
            float l;
            if (bl3) {
                k = 28;
                l = bl ? 0.5F : 0.1F;
            } else {
                k = bl ? 24 : 32;
                l = bl ? 0.6F : 0.35F;
            }

            float o = (float)this.getPoseTime() + f;
            boolean bl4 = o < (float)k;
            float p = bl4 ? o / (float)k : (o - (float)k) / (float)(j - k);
            float q = g - l * h;
            d += bl3 ? (double)Mth.lerp(p, bl4 ? g : q, bl4 ? q : i) : (double)Mth.lerp(p, bl4 ? i - g : i - q, bl4 ? i - q : 0.0F);
        }

        if (bl3 && !bl2) {
            d += (double)i;
        }

        return d;
    }

    @Override
    public Vec3 getLeashOffset(float tickDelta) {
        return new Vec3(0.0D, this.getBodyAnchorAnimationYOffset(true, tickDelta) - (double)(0.2F * this.getScale()), (double)(this.getBbWidth() * 0.56F));
    }

    @Override
    public double getPassengersRidingOffset() {
        return (double)(this.getDimensions(this.getPose()).height - (this.isBaby() ? 0.35F : 0.6F));
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (this.getControllingPassenger() != passenger) {
            this.clampRotation(passenger);
        }

    }

    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(this.getYRot());
        float f = passenger.getYRot();
        float g = Mth.wrapDegrees(f - this.getYRot());
        float h = Mth.clamp(g, -160.0F, 160.0F);
        passenger.yRotO += h - g;
        float i = f + h - g;
        passenger.setYRot(i);
        passenger.setYHeadRot(i);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() <= 2;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (!this.getPassengers().isEmpty() && this.isSaddled()) {
            Entity entity = this.getPassengers().get(0);
            if (entity instanceof LivingEntity) {
                return (LivingEntity)entity;
            }
        }

        return null;
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    public boolean isPoseSitting() {
        return this.getPose() == Pose.SITTING;
    }

    public boolean isInPoseTransition() {
        long l = this.getPoseTime();
        boolean var10000;
        switch (this.getPose()) {
            case STANDING:
                var10000 = l < 52L;
                break;
            case SITTING:
                var10000 = l < 40L;
                break;
            default:
                var10000 = false;
        }

        return var10000;
    }

    private boolean isSittingDown() {
        return this.getPose() == Pose.SITTING && this.getPoseTime() < 40L;
    }

    public void sitDown() {
        if (!this.hasPose(Pose.SITTING)) {
            this.playSound(SoundEvents.CAMEL_SIT, 1.0F, 1.0F);
            this.setPose(Pose.SITTING);
            this.resetLastPoseChangeTick(this.level.getGameTime());
        }
    }

    public void standUp() {
        if (!this.hasPose(Pose.STANDING)) {
            this.playSound(SoundEvents.CAMEL_STAND, 1.0F, 1.0F);
            this.setPose(Pose.STANDING);
            this.resetLastPoseChangeTick(this.level.getGameTime());
        }
    }

    public void standUpPanic() {
        this.setPose(Pose.STANDING);
        this.resetLastPoseChangeTick(this.level.getGameTime() - 52L);
    }

    @VisibleForTesting
    public void resetLastPoseChangeTick(long lastPoseTick) {
        this.entityData.set(LAST_POSE_CHANGE_TICK, lastPoseTick);
    }

    public long getPoseTime() {
        return this.level.getGameTime() - this.entityData.get(LAST_POSE_CHANGE_TICK);
    }

    @Override
    public SoundEvent getSaddleSoundEvent() {
        return SoundEvents.CAMEL_SADDLE;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (!this.firstTick && DASH.equals(data)) {
            this.dashCooldown = this.dashCooldown == 0 ? 55 : this.dashCooldown;
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Camel.CamelBodyRotationControl(this);
    }

    @Override
    public boolean isTamed() {
        return true;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.level.isClientSide) {
            player.openHorseInventory(this, this.inventory);
        }

    }

    class CamelBodyRotationControl extends BodyRotationControl {
        public CamelBodyRotationControl(Camel camel) {
            super(camel);
        }

        @Override
        public void clientTick() {
            if (!Camel.this.refuseToMove()) {
                super.clientTick();
            }

        }
    }
}
