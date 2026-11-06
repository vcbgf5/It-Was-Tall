package com.itwastall.entity;

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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A dead animal lying on its side (cow, sheep or pig): part of the animal circle. It never moves; a hit from a
 * player clears it away (a little rotten flesh is all that is left).
 */
public class CarcassEntity extends PathAwareEntity {
    public static final int COW = 0, SHEEP = 1, PIG = 2;
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(CarcassEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public CarcassEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setAiDisabled(true);
        setSilent(true);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
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

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO.add(0, getVelocity().y, 0));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.getAttacker() instanceof PlayerEntity) {
            playSound(SoundEvents.BLOCK_HONEY_BLOCK_BREAK, 0.8f, 0.6f);
            dropStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.ROTTEN_FLESH, 1 + random.nextInt(2)));
            discard();
            return true;
        }
        return false;
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
    protected void initGoals() {
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", variant());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setVariant(nbt.getInt("Variant"));
        setAiDisabled(true);
    }
}
