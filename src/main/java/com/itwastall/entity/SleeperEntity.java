package com.itwastall.entity;

import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModDimensions;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ChestBlock;
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
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * THE SLEEPER. In one of the great halls of the dream (OUTSPACE) someone lies in a bed — wearing YOUR
 * face (the client draws it with the skin of whoever is looking). Walk past quietly and it sleeps on.
 * Run, jump, or open the chest by its bed and it sits up — and the Old One of your dream knows exactly
 * where you are and comes faster. By the bed: a chest with a Warding Crystal. Book: THE SLEEPER.
 */
public class SleeperEntity extends PathAwareEntity {
    private static final TrackedData<Boolean> AWAKE = DataTracker.registerData(SleeperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final double HEARING = 10.0;

    @Nullable
    private BlockPos bed;
    private int awakeTicks;
    /** Set by the chest hook: someone opened a container right next to it. */
    private boolean disturbed;

    public SleeperEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(AWAKE, false);
    }

    public boolean isAwake() {
        return dataTracker.get(AWAKE);
    }

    @Override
    protected void initGoals() {
    }

    /** Lies down in the bed whose HEAD part is at {@code head}. */
    public void lieIn(BlockPos head) {
        bed = head.toImmutable();
        sleep(bed);
    }

    // ------------------------------------------------------------------ ticking ----

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (isAwake()) {
            PlayerEntity p = world.getClosestPlayer(this, 24);
            if (p != null) {
                float yaw = (float) (MathHelper.atan2(p.getZ() - getZ(), p.getX() - getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
                setYaw(yaw);
                setBodyYaw(yaw);
                setHeadYaw(yaw);
                getLookControl().lookAt(p, 30f, 30f);
            }
            if (++awakeTicks > 20 * 20) discard();          // it stood up, stared, and is gone
            return;
        }
        // keep lying in the bed (vanilla stops "sleeping" if the bed is gone)
        if (bed != null && !isSleeping()) {
            if (world.getBlockState(bed).getBlock() instanceof BedBlock) sleep(bed);
            else {
                discard();
                return;
            }
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.squaredDistanceTo(this) > HEARING * HEARING) continue;
            boolean noise = !p.isSneaking() && (p.isSprinting() || (!p.isOnGround() && p.getVelocity().y > 0.2));
            if (noise || disturbed) {
                wake(p);
                return;
            }
        }
        disturbed = false;
    }

    /** It sits up: the dream's Old One now knows where you are. */
    public void wake(ServerPlayerEntity player) {
        if (isAwake()) return;
        dataTracker.set(AWAKE, true);
        awakeTicks = 0;
        wakeUp();
        getLookControl().lookAt(player, 30f, 30f);
        float yaw = (float) (MathHelper.atan2(player.getZ() - getZ(), player.getX() - getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        setHeadYaw(yaw);
        setBodyYaw(yaw);
        ModNetworking.sendScareText(player, "it is awake.");
        MineEvents.playTo(player, ModSounds.AMBIENT_STING, getEyePos(), 1.0f, 0.7f);
        MineEvents.playTo(player, ModSounds.SCARE_BREATH, getEyePos(), 1.0f, 0.8f);
        JournalLog.add(player, "The one in the bed had my face. It sat up and looked at me.");
        if (player.getWorld().getRegistryKey() == ModDimensions.OUTSPACE) {
            for (OldOneEntity one : ((ServerWorld) getWorld()).getEntitiesByType(ModEntities.OLD_ONE,
                    e -> e.isDream() && player.getUuid().equals(e.victimUuid()))) {
                one.alert(player);
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof ServerPlayerEntity sp) wake(sp);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (bed != null) nbt.put("Bed", NbtHelper.fromBlockPos(bed));
        nbt.putBoolean("Awake", isAwake());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        NbtHelper.toBlockPos(nbt, "Bed").ifPresent(p -> bed = p);
        dataTracker.set(AWAKE, nbt.getBoolean("Awake"));
    }

    // ------------------------------------------------------------------ hooks ----

    public static void init() {
        // opening a chest or barrel right by a sleeper wakes it
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            var block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (!(block instanceof ChestBlock) && !(block instanceof BarrelBlock)) return ActionResult.PASS;
            for (SleeperEntity s : ((ServerWorld) world).getEntitiesByType(ModEntities.SLEEPER,
                    new net.minecraft.util.math.Box(hit.getBlockPos()).expand(6), e -> !e.isAwake())) {
                s.disturbed = true;
                if (!sp.isSneaking()) s.wake(sp);
            }
            return ActionResult.PASS;
        });
    }

    /** /itwastall event sleeper: a bed and a sleeper right in front of the player (any world). */
    public static boolean placeFor(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        var facing = player.getHorizontalFacing();
        BlockPos foot = player.getBlockPos().offset(facing, 3);
        BlockPos head = foot.offset(facing);
        if (!world.isAir(foot) || !world.isAir(head) || world.isAir(foot.down()) || world.isAir(head.down())) return false;
        return makeBed(world, head, facing) != null;
    }

    /** Places a red bed (head at {@code head}, the foot behind it) and a sleeper in it. */
    @Nullable
    public static SleeperEntity makeBed(ServerWorld world, BlockPos head, net.minecraft.util.math.Direction facing) {
        BlockPos foot = head.offset(facing.getOpposite());
        var bedState = net.minecraft.block.Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, facing);
        world.setBlockState(foot, bedState.with(BedBlock.PART, net.minecraft.block.enums.BedPart.FOOT), 3);
        world.setBlockState(head, bedState.with(BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD), 3);
        SleeperEntity s = ModEntities.SLEEPER.create(world);
        if (s == null) return null;
        s.refreshPositionAndAngles(head.getX() + 0.5, head.getY() + 0.6875, head.getZ() + 0.5, facing.asRotation(), 0f);
        world.spawnEntity(s);
        s.lieIn(head);
        return s;
    }
}
