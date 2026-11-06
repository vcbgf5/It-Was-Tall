package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE DEEP ONE. Below Y 0 the tunnels are not empty: something long and thin crawls upside down on
 * the ceiling. You only ever see it from the corner of your eye — look straight at it and it is gone.
 * It puts out the torches behind you. It hears you RUN (and walk, closer by), but not sneak. If it
 * reaches you, it shakes the block right out of your hand (it drops to the ground) and is gone again.
 * The book THE DEEP ONE (deep_lair structure) tells all of this.
 */
public class DeepOneEntity extends HostileEntity implements GeoEntity {
    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.deep_one.idle");
    public static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("animation.deep_one.crawl");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID victim;
    private int ticks;
    private boolean crawling;

    public DeepOneEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0);
    }

    public void setVictim(PlayerEntity player) {
        victim = player.getUuid();
    }

    @Override
    protected void initGoals() {
    }

    // ------------------------------------------------------------------ ticking ----

    @Override
    protected void mobTick() {
        super.mobTick();
        ticks++;
        setVelocity(Vec3d.ZERO);
        PlayerEntity p = victim == null ? null : getWorld().getPlayerByUuid(victim);
        if (!(p instanceof ServerPlayerEntity player) || !player.isAlive() || player.getWorld() != getWorld()
                || squaredDistanceTo(player) > 64 * 64 || ticks > 20 * 120) {
            discard();
            return;
        }
        // only from the corner of the eye: a straight look and it is not there anymore
        if (lookedAtBy(player)) {
            vanish(player, "Something long was hanging from the ceiling. When I looked, there was nothing.");
            return;
        }
        // it listens: running carries far, walking only close by, sneaking not at all
        double hearing = player.isSneaking() ? 0 : player.isSprinting() ? 26 : (player.getVelocity().horizontalLengthSquared() > 0.002 ? 9 : 0);
        double dist = distanceTo(player);
        crawling = dist < hearing;
        if (crawling) crawlTowards(player);
        faceTowards(player);
        // the torches behind you go out
        if (ticks % 60 == 30) douseTorchBehind(player);
        if (ticks % 90 == 45) MineEvents.playTo(player, ModSounds.AMBIENT_BREATH, getPos(), 0.6f, 0.6f);
        // it reached you: the block in your hand drops, and it is gone
        double dx = player.getX() - getX(), dz = player.getZ() - getZ();
        if (dx * dx + dz * dz < 1.6 * 1.6 && getY() - player.getY() < 4.5) {
            ItemStack held = player.getMainHandStack();
            if (!held.isEmpty() && held.getItem() instanceof BlockItem) {
                ItemStack drop = held.copy();
                player.getInventory().removeStack(player.getInventory().selectedSlot);
                player.dropItem(drop, false, true);
                player.sendMessage(net.minecraft.text.Text.literal("Something knocked it out of your hands.")
                        .formatted(net.minecraft.util.Formatting.DARK_GRAY, net.minecraft.util.Formatting.ITALIC), true);
            }
            com.itwastall.net.ModNetworking.sendScareText(player, "it was above you.");
            if (Director.phase() == 1) Director.addTension(2.0);
            vanish(player, "Something dropped from the ceiling. What I was holding fell to the ground.");
        }
    }

    /** Within the central view and in plain sight (the corner of the eye is fine). */
    private boolean lookedAtBy(PlayerEntity player) {
        Vec3d to = getPos().add(0, 0.4, 0).subtract(player.getEyePos());
        double d = to.length();
        if (d < 1e-3) return true;
        return player.getRotationVec(1f).dotProduct(to.multiply(1 / d)) > 0.93 && player.canSee(this);
    }

    /** Moves along the ceiling: it stays hanging under a solid block; no ceiling ahead, no step. */
    private void crawlTowards(PlayerEntity player) {
        Vec3d dir = new Vec3d(player.getX() - getX(), 0, player.getZ() - getZ());
        if (dir.lengthSquared() < 1e-4) return;
        Vec3d step = dir.normalize().multiply(0.09);
        double nx = getX() + step.x, nz = getZ() + step.z;
        for (int dy : new int[]{0, 1, -1}) {
            double ny = getY() + dy;
            if (hangsAt(nx, ny, nz)) {
                setPosition(nx, ny, nz);
                return;
            }
        }
    }

    /** True when there is room for the body at (x, y, z) and a solid block right above its back. */
    private boolean hangsAt(double x, double y, double z) {
        World w = getWorld();
        BlockPos top = BlockPos.ofFloored(x, y + getHeight() + 0.05, z);
        BlockState above = w.getBlockState(top);
        if (above.getCollisionShape(w, top).isEmpty()) return false;
        return w.isSpaceEmpty(this, getBoundingBox().offset(x - getX(), y - getY(), z - getZ()));
    }

    private void douseTorchBehind(ServerPlayerEntity player) {
        World w = getWorld();
        Vec3d look = player.getRotationVec(1f);
        BlockPos base = player.getBlockPos();
        for (BlockPos p : BlockPos.iterateOutwards(base, 7, 3, 7)) {
            BlockState st = w.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.block.TorchBlock)) continue;
            Vec3d to = Vec3d.ofCenter(p).subtract(player.getEyePos()).normalize();
            if (look.dotProduct(to) > -0.2) continue;                      // only the ones behind you
            w.breakBlock(p, true);                                           // the torch drops, the light is gone
            MineEvents.playTo(player, SoundEvents.BLOCK_FIRE_EXTINGUISH, Vec3d.ofCenter(p), 0.6f, 0.7f);
            return;
        }
    }

    private void vanish(ServerPlayerEntity player, String journal) {
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 12, 0.4, 0.2, 0.4, 0.01);
        }
        MineEvents.playTo(player, ModSounds.SCARE_BREATH, getPos(), 0.8f, 0.7f);
        JournalLog.add(player, journal);
        discard();
    }

    private void faceTowards(PlayerEntity player) {
        float yaw = (float) (MathHelper.atan2(player.getZ() - getZ(), player.getX() - getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // hitting it is like looking straight at it
        if (source.getAttacker() instanceof ServerPlayerEntity sp) vanish(sp, "I hit something on the ceiling. It was not there anymore.");
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
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

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
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
        setNoGravity(true);
    }

    // ------------------------------------------------------------------ GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animation));
    }

    private PlayState animation(AnimationState<DeepOneEntity> state) {
        return state.setAndContinue(state.isMoving() ? CRAWL : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ------------------------------------------------------------------ spawning ----

    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DeepOneEntity::directorTick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
    }

    private static void directorTick(MinecraftServer server) {
        if (server.getTicks() % 200 != 100) return;                     // every 10 s
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.deepOne || Director.phase() != 1 || Director.stageIndex() < cfg.deepOneMinStage) return;
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isCreative() || p.isSpectator() || p.getWorld().getRegistryKey() != World.OVERWORLD) continue;
            if (p.getY() > cfg.deepOneMaxY || !MineEvents.isUnderground(p)) continue;
            Long next = NEXT.get(p.getUuid());
            if (next != null && now < next) continue;
            if (!p.getServerWorld().getEntitiesByType(ModEntities.DEEP_ONE, p.getBoundingBox().expand(64), e -> true).isEmpty()) continue;
            if (p.getRandom().nextDouble() >= MathHelper.clamp(cfg.deepOneChance, 0.0, 1.0)) continue;
            if (spawnFor(p)) NEXT.put(p.getUuid(), now + 20L * 60 * (4 + p.getRandom().nextInt(4)));
        }
    }

    /**
     * Hangs one on the ceiling 9-18 blocks away, off to the side of the player's view (the corner of
     * the eye), with clear line of sight. Also /itwastall event deepone.
     */
    public static boolean spawnFor(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random r = player.getRandom();
        float yaw = player.getYaw();
        for (int attempt = 0; attempt < 40; attempt++) {
            float side = (r.nextBoolean() ? 1 : -1) * (40f + r.nextFloat() * 45f);     // 40-85 degrees off-centre
            float a = (yaw + side) * MathHelper.RADIANS_PER_DEGREE;
            double dist = 9 + r.nextDouble() * 9;
            double x = player.getX() - MathHelper.sin(a) * dist, z = player.getZ() + MathHelper.cos(a) * dist;
            // find the ceiling above that column, a few blocks over the player's feet
            BlockPos.Mutable m = new BlockPos.Mutable(x, player.getY() + 1, z);
            int ceiling = Integer.MIN_VALUE;
            for (int dy = 1; dy <= 7; dy++) {
                m.setY((int) Math.floor(player.getY()) + dy);
                if (!world.getBlockState(m).getCollisionShape(world, m).isEmpty()) {
                    ceiling = m.getY();
                    break;
                }
            }
            if (ceiling == Integer.MIN_VALUE) continue;
            DeepOneEntity one = ModEntities.DEEP_ONE.create(world);
            if (one == null) return false;
            double y = ceiling - one.getHeight() - 0.01;
            one.refreshPositionAndAngles(x, y, z, 0f, 0f);
            if (!world.isSpaceEmpty(one)) continue;
            if (!player.canSee(one)) continue;
            one.setVictim(player);
            world.spawnEntity(one);
            return true;
        }
        return false;
    }
}
