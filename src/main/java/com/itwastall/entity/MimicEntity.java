package com.itwastall.entity;

import com.itwastall.world.JournalLog;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * THE MIMIC. Looks like a cow, a sheep or a pig — but there is blood on it, it moves too stiffly and it never
 * stops looking at you. Its sounds are wrong: slowed down, too deep, too quiet. It follows you, a few blocks
 * behind, and stands perfectly still whenever you look. Then it runs at you, hits once, and is gone in a puff
 * of smoke. A hit from you sends it away too.
 */
public class MimicEntity extends PathAwareEntity {
    public static final int COW = 0, SHEEP = 1, PIG = 2;
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(MimicEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final int FOLLOW = 0, RUSH = 1;

    private int state = FOLLOW;
    private int stateTicks;
    private int followFor = 20 * (40 + 40);   // set on spawn
    private int nextSound = 60;

    public MimicEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, COW);
    }

    public int variant() {
        return dataTracker.get(VARIANT);
    }

    public void setVariant(int v) {
        dataTracker.set(VARIANT, Math.floorMod(v, 3));
    }

    /** How long it follows before it attacks (ticks). */
    public void setFollowFor(int ticks) {
        this.followFor = ticks;
    }

    @Override
    protected void initGoals() {
    }

    // ------------------------------------------------------------------ ticking ----

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        stateTicks++;
        if (world.isDay() && age % 20 == 0) {
            vanish(world);
            return;
        }
        PlayerEntity target = world.getClosestPlayer(getX(), getY(), getZ(), 48, p -> p.isAlive() && !p.isSpectator() && !((PlayerEntity) p).isCreative());
        if (target == null) {
            getNavigation().stop();
            if (age > 20 * 60 * 3) vanish(world);
            return;
        }
        // it never takes its eyes off you
        getLookControl().lookAt(target, 40f, 40f);
        double dist = distanceTo(target);

        if (--nextSound <= 0) {
            nextSound = 80 + random.nextInt(100);
            distorted(world, ambient(), 0.35f);
        }

        if (state == FOLLOW) {
            boolean watched = isWatchedBy(target);
            if (watched) {
                getNavigation().stop();          // stiff as a statue while you look
            } else if (dist > 6 && (stateTicks % 10 == 0 || getNavigation().isIdle())) {
                getNavigation().startMovingTo(target, 1.0);
            } else if (dist <= 6) {
                getNavigation().stop();
            }
            if (stateTicks > followFor || (dist < 4 && !watched && stateTicks > 200)) {
                state = RUSH;
                stateTicks = 0;
                distorted(world, hurt(), 0.9f);
            }
        } else {
            if (stateTicks % 5 == 1 || getNavigation().isIdle()) getNavigation().startMovingTo(target, 2.3);
            if (dist < 1.9) {
                strike(world, target);
                return;
            }
            if (stateTicks > 20 * 8) vanish(world);
        }
    }

    private boolean isWatchedBy(PlayerEntity player) {
        Vec3d to = new Vec3d(getX(), getBodyY(0.5), getZ()).subtract(player.getEyePos());
        double len = to.length();
        if (len < 1.0E-3) return true;
        return player.getRotationVec(1f).dotProduct(to.multiply(1.0 / len)) > 0.8 && player.canSee(this);
    }

    private void strike(ServerWorld world, PlayerEntity target) {
        target.damage(world.getDamageSources().mobAttack(this), 4f);
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        target.takeKnockback(0.6, -dx, -dz);
        distorted(world, hurt(), 1.0f);
        if (target instanceof ServerPlayerEntity sp) {
            JournalLog.add(sp, "The animal was wrong. It followed me, it hit me, and then it was not there.");
            com.itwastall.advancement.Achievements.grant(sp, "mimic");
        }
        vanish(world);
    }

    private void vanish(ServerWorld world) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.5), getZ(), 20, 0.4, 0.4, 0.4, 0.01);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.HOSTILE, 0.5f, 0.5f);
        discard();
    }

    /** The animal's own sound, but slowed down and too deep — and quiet, as if from further away than it is. */
    private void distorted(ServerWorld world, SoundEvent sound, float volume) {
        float pitch = 0.38f + random.nextFloat() * 0.14f;
        world.playSound(null, getX(), getY(), getZ(), sound, SoundCategory.HOSTILE, volume, pitch);
        if (random.nextInt(3) == 0) {   // and a second voice under it, a beat later and lower
            world.playSound(null, getX(), getY(), getZ(), sound, SoundCategory.HOSTILE, volume * 0.6f, pitch * 0.75f);
        }
    }

    private SoundEvent ambient() {
        return switch (variant()) {
            case SHEEP -> SoundEvents.ENTITY_SHEEP_AMBIENT;
            case PIG -> SoundEvents.ENTITY_PIG_AMBIENT;
            default -> SoundEvents.ENTITY_COW_AMBIENT;
        };
    }

    private SoundEvent hurt() {
        return switch (variant()) {
            case SHEEP -> SoundEvents.ENTITY_SHEEP_HURT;
            case PIG -> SoundEvents.ENTITY_PIG_HURT;
            default -> SoundEvents.ENTITY_COW_HURT;
        };
    }

    // ------------------------------------------------------------- properties ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld() instanceof ServerWorld world && source.getAttacker() instanceof PlayerEntity) {
            distorted(world, hurt(), 0.8f);
            vanish(world);
            return true;
        }
        return super.damage(source, amount);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;   // only its own, distorted sounds
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return hurt();
    }

    @Override
    public float getSoundPitch() {
        return 0.45f;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", variant());
        nbt.putInt("FollowFor", followFor);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setVariant(nbt.getInt("Variant"));
        if (nbt.contains("FollowFor")) followFor = nbt.getInt("FollowFor");
    }

    /** Yaw that faces a point (for spawning it already looking at the player). */
    public static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) (MathHelper.atan2(toZ - fromZ, toX - fromX) * MathHelper.DEGREES_PER_RADIAN) - 90f;
    }
}
