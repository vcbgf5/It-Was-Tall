package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModSounds;
import com.itwastall.registry.ModItems;
import com.itwastall.world.ItWasTallState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "It" - the Tall One.
 * <p>
 * Behaviour: STARE (stands still and watches the player) -> CHASE (when the player comes close or keeps
 * staring while approaching) -> SINK (when the player reaches shelter it sinks into the ground) -> a 0.1 s
 * jumpscare is sent to that player and it disappears.
 * <p>
 * Underground: DIG (spawned inside the rock above a miner, it digs a 1x4 tunnel towards the player with an iron
 * pickaxe, then chases), PEEK (stands right behind the player; turning around = jumpscare). While chasing in a
 * cave it falls back to DIG when it cannot path to the player (tunnels are too low for it).
 */
public class TallOneEntity extends HostileEntity implements GeoEntity {
    /** Order matters (saved as ordinal). */
    public enum Mode {STARE, CHASE, SINK, DIG, PEEK}

    private static final TrackedData<Integer> MODE = DataTracker.registerData(TallOneEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tall_one.idle_stare");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tall_one.walk");
    public static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.tall_one.run");
    public static final RawAnimation SINK = RawAnimation.begin().thenPlayAndHold("animation.tall_one.sink");
    public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.tall_one.attack");
    public static final RawAnimation KNOCK = RawAnimation.begin().thenLoop("animation.tall_one.knock");
    public static final RawAnimation DIG = RawAnimation.begin().thenLoop("animation.tall_one.dig");

    private static final int SINK_ANIM_TICKS = 24;          // animation length 1.2 s
    private static final int STARE_SOUND_INTERVAL = 160;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID victim;
    private int modeTicks;
    private int lookedAtTicks;
    private int attackCooldown;
    private int jumpscareAt = -1;
    private double lastVictimDistance = -1;
    private int chaseStuckTicks;

    // per-chase tension-hook guards (PHASES §2: each hook fires at most once per chase/event)
    private boolean chaseTensionGiven;   // +4 at chase start, once per chase
    private boolean watchTensionGiven;   // +1 at >= 2 s of staring, once per sighting (30 min cap via ItWasTallState)

    // DIG state
    private final List<BlockPos> digCells = new ArrayList<>();
    @Nullable
    private BlockPos digNext;
    private int digTicks;
    private int digDuration;
    private int digFailures;

    public TallOneEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 20;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    // ------------------------------------------------------------------ data ----

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(MODE, Mode.STARE.ordinal());
    }

    /** Who it is after (null = nobody yet). */
    @Nullable
    public UUID getVictimUuid() {
        return victim;
    }

    public Mode getMode() {
        return Mode.values()[MathHelper.clamp(this.dataTracker.get(MODE), 0, Mode.values().length - 1)];
    }

    public void setMode(Mode mode) {
        if (getMode() != mode) {
            this.dataTracker.set(MODE, mode.ordinal());
            this.modeTicks = 0;
        }
    }

    public void setVictim(@Nullable PlayerEntity player) {
        this.victim = player == null ? null : player.getUuid();
    }

    /** Starts the window-only knock animation on a client-side False Presence. */
    public void playWindowKnockAnimation() {
        triggerAnim("action", "knock");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Mode", getMode().ordinal());
        if (victim != null) nbt.putUuid("Victim", victim);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(MODE, nbt.getInt("Mode"));
        setNoGravity(getMode() == Mode.DIG);
        if (nbt.containsUuid("Victim")) victim = nbt.getUuid("Victim");
    }

    // -------------------------------------------------------------------- AI ----

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        // Everything else is driven by the state machine in mobTick().
    }

    @Nullable
    private PlayerEntity findVictim() {
        if (victim != null) {
            PlayerEntity p = getWorld().getPlayerByUuid(victim);
            if (p != null && p.isAlive() && !p.isSpectator() && !p.isCreative()) return p;
        }
        PlayerEntity p = getWorld().getClosestPlayer(getX(), getY(), getZ(), 80.0, EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
        victim = p == null ? null : p.getUuid();
        return p;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        modeTicks++;
        if (attackCooldown > 0) attackCooldown--;

        // PHASES §1 (FAZA PRE): the mod is quiet before IT JOINED — an entity that exists anyway
        // (e.g. spawn-egg testing) freezes and quietly disappears after 30 s, without any effect.
        if (Director.phase() == 0) {
            if (modeTicks > 600) discard();
            return;
        }

        PlayerEntity player = findVictim();
        switch (getMode()) {
            case STARE -> tickStare(player);
            case CHASE -> tickChase(player);
            case SINK -> tickSink(player);
            case DIG -> tickDig(player);
            case PEEK -> tickPeek(player);
        }
    }

    private void tickStare(@Nullable PlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        Stage st = Director.currentStage(); // live per tick (PHASES §0) — no snapshot from init
        if (player == null) {
            getNavigation().stop();
            if (modeTicks > 200) discard();
            return;
        }
        // Multiplayer: anyone else who walks right up to it gets chased instead.
        PlayerEntity intruder = getWorld().getClosestPlayer(getX(), getY(), getZ(), cfg.chaseTriggerDistance,
                p -> EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(p) && p != player);
        if (intruder != null && !ShelterCheck.isSheltered(getWorld(), intruder.getBlockPos())) {
            maybeStartChase(intruder);
            return;
        }
        faceTowards(player);

        double dist = distanceTo(player);
        boolean watched = isBeingWatchedBy(player);
        lookedAtTicks = watched ? lookedAtTicks + 1 : Math.max(0, lookedAtTicks - 2);
        boolean approaching = lastVictimDistance > 0 && dist < lastVictimDistance - 0.02;
        lastVictimDistance = dist;

        // STALK: from Stage 2 onward It slowly closes the distance while unseen,
        // then freezes as soon as the player looks directly at it.
        boolean playerSheltered = ShelterCheck.isSheltered(getWorld(), player.getBlockPos());
        if (st.ordinal() >= 2 && cfg.stalking && !watched && !playerSheltered
                && dist > Math.max(2.0, cfg.stalkStopDistance)
                && dist <= Math.max(cfg.stalkStopDistance, cfg.stalkMaxDistance)
                && canSee(player)) {
            double stalkSpeed = MathHelper.clamp(cfg.stalkSpeed, 0.01, 0.35);
            if (modeTicks % 10 == 0 || getNavigation().isIdle()) {
                getNavigation().startMovingTo(player, stalkSpeed);
            }
        } else {
            // Cancel residual horizontal motion too: being spotted must freeze It immediately.
            getNavigation().stop();
            setVelocity(0.0, getVelocity().y, 0.0);
        }

        if (modeTicks == 2 && player instanceof ServerPlayerEntity sp) {
            MineEvents.playTo(sp, ModSounds.AMBIENT_STING, getEyePos(), 1.2f, 1.0f);
        }
        if (dist < 24 && modeTicks % 90 == 45) {
            getWorld().playSound(null, getBlockPos(), ModSounds.GROWL, getSoundCategory(), 0.9f, 0.8f + random.nextFloat() * 0.2f);
        }
        if (modeTicks % STARE_SOUND_INTERVAL == 1) {
            playSoundTo(player, ModSounds.STARE, 0.6f, 0.8f + random.nextFloat() * 0.2f);
        }

        maybeAwardWatchTension(player); // PHASES §2: looked at >= 2 s -> +1.0 (30 min cap per player)

        boolean tooClose = dist < cfg.chaseTriggerDistance;
        boolean staredDown = lookedAtTicks > cfg.stareTriggerSeconds * 20 && approaching;
        if ((tooClose || staredDown) && !ShelterCheck.isSheltered(getWorld(), player.getBlockPos())) {
            maybeStartChase(player); // stage-gated: chase may simply not happen (see helper)
        } else if (modeTicks > cfg.stareTimeoutSeconds * 20) {
            discard();
        }
    }

    /**
     * Stage-gated chase trigger (PHASES §3 row "CHASE"): while chase is not enabled (ETAP 0-2, and
     * ETAP 1-2 always), or when the stage's chance roll fails (ETAP 3 keeps it optional at 35%),
     * It instead quietly disappears — no scream, no jumpscare. The roll happens once, exactly when
     * the trigger conditions are first met, so a failing roll means one sighting that walks away.
     */
    private void maybeStartChase(PlayerEntity player) {
        Stage st = Director.currentStage();
        if (!st.chaseEnabled() || this.random.nextFloat() >= st.chaseChance()) {
            disappearQuiet();
            return;
        }
        startChase(player);
    }

    /** Quiet disappearance: no sound, no effect, no jumpscare ("It znika", PHASES Etapy notes). */
    private void disappearQuiet() {
        discard();
    }

    /** Ends an encounter where it had to give up: SINK when the stage allows it, quiet otherwise. */
    private void finishEncounter() {
        if (Director.currentStage().sinkEnabled()) startSink();
        else disappearQuiet();
    }

    private void startChase(PlayerEntity player) {
        setVictim(player);
        setNoGravity(false);
        clearDig();
        setMode(Mode.CHASE);
        setTarget(player);
        getWorld().playSound(null, getBlockPos(), ModSounds.SCREAM, getSoundCategory(), 2.5f, 0.85f);
        // the red chase overlay starts NOW, not on the next 1 s pulse
        if (player instanceof ServerPlayerEntity sp) ModNetworking.sendChase(sp, true);
        if (!chaseTensionGiven && Director.phase() == 1) {
            chaseTensionGiven = true;
            Director.addTension(4.0); // PHASES §2: rozpoczęcie pościgu +4, 1x na pościg (flag on entity)
        }
    }

    private void tickChase(@Nullable PlayerEntity player) {
        if (player == null || distanceTo(player) > 96) {
            discard();
            return;
        }
        Stage st = Director.currentStage(); // live per tick (PHASES §0)
        setTarget(player);
        getLookControl().lookAt(player, 30f, 30f);
        if (modeTicks % 100 == 1 && player instanceof ServerPlayerEntity sp) {
            MineEvents.playTo(sp, ModSounds.AMBIENT_HEARTBEAT, sp.getPos(), 1.0f, 1.0f);
        }

        if (ShelterCheck.isSheltered(getWorld(), player.getBlockPos())) {
            // SINK is unlocked from ETAP 4 (PHASES §3 row "SINK"); earlier stages end the chase quietly
            finishEncounter();
            return;
        }
        // PHASES §3 ETAP 5: chaseSpeed 1.6 instead of the config default; an explicitly changed
        // chaseSpeed in config/itwastall.json (the documented default is 1.45) still wins here.
        double baseSpeed = ItWasTallConfig.INSTANCE.chaseSpeed;
        double speed = st.ordinal() >= 5 && baseSpeed == 1.45 ? 1.6 : baseSpeed;
        if (modeTicks % 5 == 0 || getNavigation().isIdle()) {
            getNavigation().startMovingTo(player, speed);
        }
        // Underground tunnels are usually too low for it: if it cannot reach the player, it digs instead.
        boolean stuck = getNavigation().isIdle() || getVelocity().horizontalLengthSquared() < 1.0E-4;
        chaseStuckTicks = stuck && distanceTo(player) > 3.0 ? chaseStuckTicks + 1 : 0;
        // PHASES §3 ETAP 5 "Szyby": when blocked, It can break glass directly in front of it (the
        // player's base) with a single attack-like animation; other stages never break glass.
        if (st.ordinal() == 5 && chaseStuckTicks > 0 && chaseStuckTicks % 20 == 0) breakGlassTowards(player);
        if (chaseStuckTicks > 40 && canDig() && MineEvents.isUnderground(player)) {
            chaseStuckTicks = 0;
            startDig();
            return;
        }
        if (attackCooldown == 0 && squaredDistanceTo(player) < 5.0) {
            attackCooldown = 20;
            triggerAnim("action", "attack");
            swingHand(getActiveHand());
            tryAttack(player);
            playSound(ModSounds.ATTACK, 1.2f, 1.0f);
        }
    }

    private void startSink() {
        boolean escapedFromChase = getMode() == Mode.CHASE; // the player reached shelter during a chase
        setNoGravity(false);
        clearDig();
        setMode(Mode.SINK);
        setTarget(null);
        getNavigation().stop();
        setVelocity(Vec3d.ZERO);
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        int min = Math.max(0, cfg.jumpscareDelayMinTicks);
        int max = Math.max(min, cfg.jumpscareDelayMaxTicks);
        jumpscareAt = SINK_ANIM_TICKS + min + random.nextInt(max - min + 1);
        getWorld().playSound(null, getBlockPos(), ModSounds.SINK, getSoundCategory(), 1.8f, 0.7f);
        if (escapedFromChase && Director.phase() == 1) Director.addTension(-3.0); // PHASES §2: udana ucieczka do bazy w pościgu -3
    }

    private void tickSink(@Nullable PlayerEntity player) {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (getWorld() instanceof ServerWorld sw && modeTicks <= SINK_ANIM_TICKS && modeTicks % 2 == 0) {
            BlockState ground = getWorld().getBlockState(getBlockPos().down());
            if (!ground.isAir()) {
                sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground),
                        getX(), getY() + 0.1, getZ(), 8, 0.35, 0.05, 0.35, 0.15);
            }
        }
        if (modeTicks == SINK_ANIM_TICKS) {
            setInvisible(true);
        }
        if (jumpscareAt < 0) jumpscareAt = SINK_ANIM_TICKS + 20;
        if (modeTicks >= jumpscareAt) {
            if (player instanceof ServerPlayerEntity sp) {
                // Jumpscare is unlocked from ETAP 4 (PHASES §3 row "Jumpscare"); earlier stages
                // simply discard — the scariest card is played only when the stage allows it.
                if (Director.currentStage().jumpscareEnabled()) {
                    ModNetworking.sendJumpscare(sp);
                    if (Director.phase() == 1) Director.addTension(6.0); // PHASES §2: jumpscare +6, 1x na zdarzenie
                    // no jumpscare per config: it still doesn't like that it sank
                    ScareSounds.scareTextAfterHide(sp, random); // PHASES §3: "don't hide. just run." from ETAP 3
                }
            }
            discard();
        }
    }

    // ------------------------------------------------------------------ DIG ----

    /** Puts it into digging mode (holding an iron pickaxe). */
    public void startDig() {
        setMode(Mode.DIG);
        setTarget(null);
        getNavigation().stop();
        setNoGravity(true);
        setVelocity(Vec3d.ZERO);
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        digFailures = 0;
        clearDig();
    }

    public void startPeek() {
        getWorld().playSound(null, getBlockPos(), ModSounds.SNIFF, getSoundCategory(), 0.7f, 1.0f);
        setMode(Mode.PEEK);
        getNavigation().stop();
    }

    private boolean canDig() {
        return ItWasTallConfig.INSTANCE.digging
                && getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
    }

    private void clearDig() {
        for (int i = 0; i < digCells.size(); i++) {
            getWorld().setBlockBreakingInfo(breakId(i), digCells.get(i), -1);
        }
        digCells.clear();
        digNext = null;
        digTicks = 0;
    }

    private int breakId(int i) {
        return 0x4000000 + getId() * 8 + i;
    }

    private void tickDig(@Nullable PlayerEntity player) {
        getNavigation().stop();
        setVelocity(Vec3d.ZERO);
        if (player == null || !canDig() || modeTicks > 20 * 180) {
            finishEncounter(); // PHASES §3: SINK only when the stage allows it, otherwise quiet
            return;
        }
        faceTowards(player);

        double dist = distanceTo(player);
        if (dist < 3.5 || (dist < 12 && canSee(player) && hasRoomToWalk())) {
            startChase(player);
            return;
        }

        if (digNext == null) {
            if (!planDigStep(player)) {
                if (++digFailures > 6) finishEncounter(); // PHASES §3: SINK only when the stage allows it
            }
            return;
        }

        digTicks++;
        int stage = Math.min(9, (int) (digTicks * 10f / Math.max(1, digDuration)));
        for (int i = 0; i < digCells.size(); i++) {
            getWorld().setBlockBreakingInfo(breakId(i), digCells.get(i), stage);
        }
        if (digTicks == 1 && random.nextInt(4) == 0) {
            getWorld().playSound(null, getBlockPos(), ModSounds.SCRAPE, getSoundCategory(), 0.8f, 0.9f);
        }
        if (!digCells.isEmpty() && digTicks % 6 == 1) {
            BlockPos c = digCells.get(random.nextInt(digCells.size()));
            BlockState st = getWorld().getBlockState(c);
            getWorld().playSound(null, c, st.getSoundGroup().getHitSound(), getSoundCategory(), 1.0f, 0.6f);
            getWorld().playSound(null, c, ModSounds.DIG, getSoundCategory(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
        }
        if (digTicks >= digDuration) {
            for (int i = 0; i < digCells.size(); i++) {
                BlockPos c = digCells.get(i);
                getWorld().setBlockBreakingInfo(breakId(i), c, -1);
                getWorld().breakBlock(c, false, this);
            }
            BlockPos next = digNext;
            digCells.clear();
            digNext = null;
            digTicks = 0;
            refreshPositionAndAngles(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, getYaw(), getPitch());
        }
    }

    /** True when there is a 4-high open space to walk in front of it (so chasing makes sense). */
    private boolean hasRoomToWalk() {
        BlockPos feet = getBlockPos();
        for (int i = 0; i < 4; i++) {
            if (!getWorld().getBlockState(feet.up(i)).getCollisionShape(getWorld(), feet.up(i)).isEmpty()) return false;
        }
        return !getWorld().getBlockState(feet.down()).getCollisionShape(getWorld(), feet.down()).isEmpty();
    }

    /** Chooses the next 1-block step towards the player and the blocks that must be broken for it. */
    private boolean planDigStep(PlayerEntity player) {
        BlockPos feet = getBlockPos();
        BlockPos goal = player.getBlockPos();
        int dx = goal.getX() - feet.getX();
        int dy = goal.getY() - feet.getY();
        int dz = goal.getZ() - feet.getZ();

        List<Direction> order = new ArrayList<>();
        // Biggest difference first; going down is preferred because it spawns above the player.
        record Axis(Direction dir, int amount) {
        }
        List<Axis> axes = new ArrayList<>(List.of(
                new Axis(dx >= 0 ? Direction.EAST : Direction.WEST, Math.abs(dx)),
                new Axis(dy >= 0 ? Direction.UP : Direction.DOWN, Math.abs(dy) + (dy < 0 ? 2 : 0)),
                new Axis(dz >= 0 ? Direction.SOUTH : Direction.NORTH, Math.abs(dz))));
        axes.sort((a, b) -> Integer.compare(b.amount(), a.amount()));
        for (Axis a : axes) if (a.amount() > 0) order.add(a.dir());
        for (Direction d : Direction.values()) if (!order.contains(d) && d != Direction.UP) order.add(d);

        for (Direction dir : order) {
            List<BlockPos> cells = cellsFor(feet, dir);
            int duration = 0;
            boolean ok = true;
            List<BlockPos> toBreak = new ArrayList<>();
            for (BlockPos c : cells) {
                BlockState st = getWorld().getBlockState(c);
                if (!st.getFluidState().isEmpty()) {
                    ok = false;
                    break;
                }
                if (st.getCollisionShape(getWorld(), c).isEmpty()) continue;
                float hardness = st.getHardness(getWorld(), c);
                if (hardness < 0 || hardness >= 50f) { // bedrock, obsidian...: an iron pickaxe can't do it
                    ok = false;
                    break;
                }
                toBreak.add(c);
                duration = Math.max(duration, breakTicks(hardness));
            }
            if (!ok) continue;
            digNext = feet.offset(dir);
            digCells.addAll(toBreak);
            digDuration = Math.max(1, duration);
            digTicks = 0;
            return true;
        }
        return false;
    }

    private static List<BlockPos> cellsFor(BlockPos feet, Direction dir) {
        List<BlockPos> cells = new ArrayList<>();
        switch (dir) {
            case DOWN -> cells.add(feet.down());
            case UP -> cells.add(feet.up(4));
            default -> {
                BlockPos side = feet.offset(dir);
                for (int i = 0; i < 4; i++) cells.add(side.up(i));
            }
        }
        return cells;
    }

    /** Ticks an iron pickaxe needs for a block of the given hardness (5 * hardness), times the slowdown. */
    private static int breakTicks(float hardness) {
        return Math.max(4, (int) Math.ceil(hardness * 5f * ItWasTallConfig.INSTANCE.digSlowdown));
    }

    // ----------------------------------------------------------------- PEEK ----

    private void tickPeek(@Nullable PlayerEntity player) {
        getNavigation().stop();
        Stage st = Director.currentStage(); // live per tick (PHASES §0)
        // PEEK is unlocked from ETAP 4 (PHASES §3 row "PEEK"): if the stage changed while one was
        // standing there (or FAZA PRE re-entered), it quietly leaves instead of finishing the trick.
        if (player == null || !st.peekEnabled()) {
            disappearQuiet();
            return;
        }
        faceTowards(player);
        if (isBeingWatchedBy(player)) {
            if (player instanceof ServerPlayerEntity sp) {
                ModNetworking.sendJumpscare(sp);
                if (Director.phase() == 1) Director.addTension(6.0); // PHASES §2: jumpscare +6, 1x na zdarzenie
            }
            discard();
        } else if (modeTicks > 20 * 25 || distanceTo(player) > 24) {
            discard();
        }
    }

    // ------------------------------------------------------------- helpers ----

    /** Same idea as the enderman check: is the player looking straight at us (and can see us)? */
    public boolean isBeingWatchedBy(PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d toUs = new Vec3d(getX() - player.getX(), getBodyY(0.75) - player.getEyeY(), getZ() - player.getZ());
        double dist = toUs.length();
        double dot = look.dotProduct(toUs.normalize());
        return dot > 1.0 - 0.05 / Math.max(1.0, dist / 8.0) && player.canSee(this);
    }

    /**
     * Telepici: PHASES §2 "gracz patrzy na It >= 2 s -> +1.0, 1x na 30 min". Fired exactly when
     * {@code lookedAtTicks} crosses 2 s (40 ticks); the 30-minute cap lives in ItWasTallState's
     * generic per-player timer map (value = the world time when it may fire again).
     */
    private void maybeAwardWatchTension(PlayerEntity player) {
        if (lookedAtTicks != 40) return;              // exactly 2 s of continued staring
        if (!(getWorld() instanceof ServerWorld sw)) return;
        long now = getWorld().getTime();
        ItWasTallState state = ItWasTallState.getOrCreate(sw.getServer());
        Long nextAllowed = state.getPlayerTimer(player.getUuid());
        if (nextAllowed != null && now < nextAllowed) return;
        state.putPlayerTimer(player.getUuid(), now + 20L * 60 * 30); // 1x na 30 min (PHASES §2)
        if (Director.phase() == 1) Director.addTension(1.0); // PHASES §2: patrzene >= 2 s +1.0
    }

    /**
     * STAGE_5 glass breaking (PHASES §3 ETAP 5 "Szyby — It może rozbicie szyby do bazy gracza,
     * tylko Stage 5"): while blocked, checks the column directly between It and the player and
     * breaks one glass block with an attack-like animation + sound. Kept deliberately simple.
     */
    private void breakGlassTowards(PlayerEntity player) {
        if (!ShelterCheck.isSheltered(getWorld(), player.getBlockPos())) return; // only the player's base
        Vec3d dir = new Vec3d(player.getX() - getX(), 0, player.getZ() - getZ());
        if (dir.lengthSquared() < 1.0E-4) return;
        Direction face = Direction.getFacing((float) dir.x, 0f, (float) dir.z);
        BlockPos column = getBlockPos().offset(face);
        for (int y = 0; y < 3; y++) {
            BlockPos p = column.up(y);
            BlockState state = getWorld().getBlockState(p);
            if (!isGlass(state) || state.getHardness(getWorld(), p) < 0) continue;
            triggerAnim("action", "attack");
            swingHand(getActiveHand());
            playSound(ModSounds.ATTACK, 1.0f, 1.0f);
            getWorld().breakBlock(p, false, this);
            return;
        }
    }

    /** Glass the stage-5 window breaking may eat: the vanilla impermeable tag (glass, tinted
     *  glass) plus stained glass blocks and glass panes (incl. stained panes). */
    private static boolean isGlass(BlockState state) {
        return state.isIn(net.minecraft.registry.tag.BlockTags.IMPERMEABLE)
                || state.getBlock() instanceof net.minecraft.block.StainedGlassBlock
                || state.getBlock() instanceof net.minecraft.block.PaneBlock;
    }

    private void faceTowards(PlayerEntity player) {
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        getLookControl().lookAt(player, 30f, 30f);
    }

    /** Only the haunted player hears it (other players on the server don't). */
    private void playSoundTo(PlayerEntity player, SoundEvent sound, float volume, float pitch) {
        if (player instanceof ServerPlayerEntity sp) MineEvents.playTo(sp, sound, getEyePos(), volume, pitch);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Ordinary weapons cannot defeat It. The Warding Crystal is the intended story solution.
        if (!(source.getAttacker() instanceof PlayerEntity player)
                || !player.getMainHandStack().isOf(ModItems.WARDING_CRYSTAL)) {
            return false;
        }
        return super.damage(source, Math.max(amount, 1000.0f));
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return getMode() != Mode.SINK && super.isPushable();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.STEP, 0.35f, 0.6f);
    }

    // -------------------------------------------------------------- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::mainAnimation));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("knock", KNOCK));
    }

    private PlayState mainAnimation(AnimationState<TallOneEntity> state) {
        return switch (getMode()) {
            case SINK -> state.setAndContinue(SINK);
            case DIG -> state.setAndContinue(DIG);
            case PEEK -> state.setAndContinue(IDLE);
            case CHASE -> state.setAndContinue(state.isMoving() ? RUN : IDLE);
            case STARE -> state.setAndContinue(state.isMoving() ? WALK : IDLE);
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
