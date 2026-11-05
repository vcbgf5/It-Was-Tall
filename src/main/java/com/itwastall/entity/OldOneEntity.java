package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * THE OLD ONE — "Skinwalker Steve". Looks like an ordinary player (the game's own Steve skin with
 * hollow eyes, a name tag reading "Old One") and stands close by, staring at you. It only moves when
 * you are not looking. Walk up to it (or hit it) and the body tears open: the head cracks in two,
 * the chest opens like a pair of doors, the empty skin collapses — and the Tall One climbs out of it
 * and runs at you. If a Tall One already exists on the server (one-per-server rule), nothing comes
 * out; you only get its face for a tenth of a second.
 */
public class OldOneEntity extends HostileEntity implements GeoEntity {
    private static final TrackedData<Boolean> SPLITTING = DataTracker.registerData(OldOneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.old_one.idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.old_one.walk");
    public static final RawAnimation SPLIT = RawAnimation.begin().thenPlayAndHold("animation.old_one.split");

    /** Split timeline (ticks): It starts climbing out, the empty skin is gone. */
    private static final int EMERGE_AT = 10, GONE_AT = 48;
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.45f, 0.02f, 0.02f), 1.4f);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID victim;
    private int ticks;
    private int splitTicks;

    public OldOneEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setCustomName(Text.literal("Old One"));
        setCustomNameVisible(true);   // a name tag over its head, just like a player's
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SPLITTING, false);
    }

    public boolean isSplitting() {
        return dataTracker.get(SPLITTING);
    }

    public void setVictim(@Nullable PlayerEntity player) {
        victim = player == null ? null : player.getUuid();
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Nullable
    private PlayerEntity findVictim() {
        if (victim != null) {
            PlayerEntity p = getWorld().getPlayerByUuid(victim);
            if (p != null && p.isAlive() && !p.isSpectator()) return p;
        }
        PlayerEntity p = getWorld().getClosestPlayer(getX(), getY(), getZ(), 48, EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
        victim = p == null ? null : p.getUuid();
        return p;
    }

    // ---------------------------------------------------------------------- ticking ----

    @Override
    protected void mobTick() {
        super.mobTick();
        ticks++;
        PlayerEntity player = findVictim();
        if (isSplitting()) {
            tickSplit(player);
            return;
        }
        if (player == null) {
            if (ticks > 200) discard();
            return;
        }
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        face(player);
        double dist = distanceTo(player);
        if (dist < Math.max(1.5, cfg.oldOneSplitDistance)) {
            startSplit(player);
            return;
        }
        boolean watched = isBeingWatchedBy(player);
        // it never moves while you look at it; when you look away it takes a few steps closer
        if (!watched && dist > 6 && ticks % 40 == 0) {
            getNavigation().startMovingTo(player, 0.8);
        } else if (watched || dist <= 6) {
            getNavigation().stop();
            setVelocity(0, getVelocity().y, 0);
        }
        if (ticks % 160 == 80 && player instanceof ServerPlayerEntity sp) {
            MineEvents.playTo(sp, ModSounds.AMBIENT_BREATH, getEyePos(), 0.5f, 1.15f);   // a little too human
        }
        // you never came closer: once you stop looking, it is simply gone
        if (ticks > 20 * 150 && !watched) discard();
    }

    public void startSplit(PlayerEntity player) {
        if (isSplitting()) return;
        setVictim(player);
        dataTracker.set(SPLITTING, true);
        setCustomNameVisible(false);
        splitTicks = 0;
        getNavigation().stop();
        setVelocity(Vec3d.ZERO);
        getWorld().playSound(null, getBlockPos(), ModSounds.OLD_ONE_TEAR, getSoundCategory(), 1.6f, 1.0f);
        if (player instanceof ServerPlayerEntity sp) JournalLog.add(sp, "It looked like a player. It wasn't. Something climbed out of it.");
    }

    private void tickSplit(@Nullable PlayerEntity player) {
        splitTicks++;
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (getWorld() instanceof ServerWorld sw && splitTicks <= 36 && splitTicks % 2 == 0) {
            sw.spawnParticles(BLOOD, getX(), getBodyY(0.6), getZ(), 6, 0.25, 0.4, 0.25, 0.02);
        }
        if (splitTicks == EMERGE_AT) emerge(player);
        if (splitTicks >= GONE_AT) discard();
    }

    /** It climbs out — unless the one Tall One allowed on the server is already somewhere else. */
    private void emerge(@Nullable PlayerEntity player) {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        int limit = Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts);
        if (HauntDirector.countActive(sw.getServer()) >= limit) {
            if (player instanceof ServerPlayerEntity sp) ModNetworking.sendJumpscare(sp);
            return;
        }
        TallOneEntity it = ModEntities.TALL_ONE.create(sw);
        if (it == null) return;
        it.refreshPositionAndAngles(getX(), getY(), getZ(), getYaw(), 0f);
        it.setVictim(player);
        sw.spawnEntity(it);
        it.startEmerge(player);
        if (Director.phase() == 1) Director.addTension(4.0);
    }

    // ---------------------------------------------------------------------- helpers ----

    private boolean isBeingWatchedBy(PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d to = new Vec3d(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double d = to.length();
        return look.dotProduct(to.normalize()) > 1.0 - 0.08 / Math.max(1.0, d / 8.0) && player.canSee(this);
    }

    private void face(PlayerEntity player) {
        double dx = player.getX() - getX(), dz = player.getZ() - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        getLookControl().lookAt(player, 30f, 30f);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isSplitting()) return false;
        if (source.getAttacker() instanceof PlayerEntity p) {
            startSplit(p);            // you touched it — that was enough
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isSplitting() && super.isPushable();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;   // it does not make sounds of its own
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (victim != null) nbt.putUuid("Victim", victim);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Victim")) victim = nbt.getUuid("Victim");
        setCustomName(Text.literal("Old One"));
        setCustomNameVisible(true);
    }

    // ---------------------------------------------------------------------- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animation));
    }

    private PlayState animation(AnimationState<OldOneEntity> state) {
        if (isSplitting()) return state.setAndContinue(SPLIT);
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
