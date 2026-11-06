package com.itwastall.entity;

import com.itwastall.advancement.Achievements;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * THE HANGED. At night, at the edge of the forest, someone hangs from a branch on a rope — wearing YOUR
 * face (the client draws it with the skin of whoever is looking, like the Sleeper). It sways, the branch
 * creaks, and it stays until morning. It never moves while you look at it; but look away and look back,
 * and its head has turned to you.
 */
public class HangedEntity extends PathAwareEntity {
    /** Height of the rope's top (the underside of the branch) above the entity's feet, for the client. */
    private static final TrackedData<Float> ROPE_TOP = DataTracker.registerData(HangedEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Neck height of the (0.9375-scaled) player model above the feet. */
    public static final float NECK = 1.4f;

    @Nullable
    private BlockPos anchor;
    private int nextCreak = 60;
    private final Set<UUID> seenBy = new HashSet<>();

    public HangedEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setNoGravity(true);
        setAiDisabled(true);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ROPE_TOP, NECK + 1.4f);
    }

    public float ropeTop() {
        return dataTracker.get(ROPE_TOP);
    }

    /** Hangs it from the underside of {@code branch} on a rope of {@code ropeLength} blocks, facing {@code yaw}. */
    public void hangFrom(BlockPos branch, float ropeLength, float yaw) {
        anchor = branch.toImmutable();
        double feet = branch.getY() - ropeLength - NECK;
        refreshPositionAndAngles(branch.getX() + 0.5, feet, branch.getZ() + 0.5, yaw, 0f);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        dataTracker.set(ROPE_TOP, (float) (branch.getY() - feet));
    }

    // ------------------------------------------------------------------ ticking ----

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (anchor != null && age % 20 == 0) {
            // morning, or the branch is gone (tree cut down): it is simply not there anymore
            if (world.isDay() || world.getBlockState(anchor).isAir()) {
                discard();
                return;
            }
        }
        if (age % 10 != 0) return;

        ServerPlayerEntity nearest = null;
        double best = 64 * 64;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            double d = p.squaredDistanceTo(this);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return;

        boolean watched = isLookedAtBy(nearest);
        if (watched) {
            if (best < 48 * 48 && seenBy.add(nearest.getUuid())) {
                JournalLog.add(nearest, "Something was hanging from a tree. It had my face.");
                Achievements.grant(nearest, "hanged");
            }
        } else {
            // the head only ever turns while nobody looks — never in front of you
            float toPlayer = (float) (MathHelper.atan2(nearest.getZ() - getZ(), nearest.getX() - getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
            float rel = MathHelper.clamp(MathHelper.wrapDegrees(toPlayer - bodyYaw), -120f, 120f);
            setHeadYaw(bodyYaw + rel);
        }

        // the branch creaks under the weight
        nextCreak -= 10;
        if (nextCreak <= 0) {
            nextCreak = 120 + random.nextInt(120);
            if (best < 32 * 32) {
                world.playSound(null, getX(), getY() + ropeTop(), getZ(), ModSounds.AMBIENT_CREAK, SoundCategory.HOSTILE,
                        ModSounds.vol(0.6f), 0.6f + random.nextFloat() * 0.2f);
            }
        }
    }

    private boolean isLookedAtBy(ServerPlayerEntity player) {
        Vec3d chest = new Vec3d(getX(), getY() + 1.0, getZ());
        Vec3d to = chest.subtract(player.getEyePos());
        double dist = to.length();
        if (dist < 1.0E-3) return true;
        double dot = player.getRotationVec(1f).dotProduct(to.multiply(1.0 / dist));
        return dot > 0.86 && player.canSee(this);
    }

    // ------------------------------------------------------------- properties ----

    @Override
    protected void initGoals() {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(net.minecraft.entity.Entity entity) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (anchor != null) nbt.put("Anchor", NbtHelper.fromBlockPos(anchor));
        nbt.putFloat("RopeTop", ropeTop());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        anchor = NbtHelper.toBlockPos(nbt, "Anchor").orElse(null);
        if (nbt.contains("RopeTop")) dataTracker.set(ROPE_TOP, nbt.getFloat("RopeTop"));
        setNoGravity(true);
        setAiDisabled(true);
    }
}
