package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModSounds;
import com.itwastall.registry.ModItems;
import com.itwastall.world.ItWasTallState;
import com.itwastall.world.JournalLog;
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
 * <p>
 * State machine (MODPLAN §3), one {@link Mode} per state:
 * <ul>
 *   <li>IDLE — nobody to haunt; waits a moment, then leaves.</li>
 *   <li>STARE — stands and watches. → WATCHED (you look at it), STALK (stage 2+, creeps closer while unseen),
 *       MOVE_UNSEEN (far away and unseen: it is somewhere closer next time you look), FEINT (stage 3+),
 *       CHASE (you came too close / stared it down), DISAPPEAR (timeout or the stage says no chase).</li>
 *   <li>STALK / WATCHED — the Weeping-Angel part: moves only while unseen, freezes the instant you look.</li>
 *   <li>TREE — peeks out from behind a tree trunk; when you notice it, it steps back behind the trunk and is gone.</li>
 *   <li>FEINT — a fake attack: screams and runs at you, and a few blocks away it is simply not there anymore.</li>
 *   <li>KNOCK — stage 5: stands outside your door and knocks. Open it and it comes in after you.</li>
 *   <li>CHASE → ATTACK (a short wind-up and one hit) → CHASE; SINK when you reach shelter; DIG underground.</li>
 *   <li>DISAPPEAR — it waits until you are not looking at it and then it is gone (never a visible teleport),
 *       at most a few seconds.</li>
 * </ul>
 * Modes are saved by ordinal, so new states are only ever appended to the enum.
 */
public class TallOneEntity extends HostileEntity implements GeoEntity {
    /** Order matters (saved as ordinal). */
    public enum Mode {STARE, CHASE, SINK, DIG, PEEK, EMERGE, IDLE, STALK, WATCHED, MOVE_UNSEEN, ATTACK, DISAPPEAR, FEINT, TREE, KNOCK}

    private static final TrackedData<Integer> MODE = DataTracker.registerData(TallOneEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tall_one.idle_stare");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tall_one.walk");
    public static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.tall_one.run");
    public static final RawAnimation SINK = RawAnimation.begin().thenPlayAndHold("animation.tall_one.sink");
    public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.tall_one.attack");
    public static final RawAnimation KNOCK = RawAnimation.begin().thenLoop("animation.tall_one.knock");
    public static final RawAnimation DIG = RawAnimation.begin().thenLoop("animation.tall_one.dig");
    public static final RawAnimation EMERGE = RawAnimation.begin().thenPlayAndHold("animation.tall_one.emerge");
    /** Ticks of the emerge animation (1.6 s) before it starts the chase. */
    private static final int EMERGE_TICKS = 32;

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
    /** Ticks of the whole sighting (STARE/STALK/WATCHED/MOVE_UNSEEN share it; modeTicks restarts per state). */
    private int sightTicks;
    private boolean watchedNow;
    private int unwatchedTicks;
    private boolean feintRolled;
    /** Slot in the world data (MODPLAN §4) claimed at least once. */
    private boolean slotClaimed;
    /** Spawned by a command or a spawn egg: always chases when triggered, whatever the stage says. */
    private boolean forced;
    /** Overworld time of its last AI tick (an It that does not tick does not block the server limit). */
    private long lastTickTime;
    /** God mode: the god whose body this It is (it only mirrors them, no AI of its own); null for a real It. */
    @Nullable
    private UUID puppetOwner;
    /** Ticks until It may say something again (IT SPEAKS). */
    private int voiceCooldown = 200;
    // TREE: where it hides (directly behind the trunk) once you have seen it
    @Nullable
    private Vec3d treeHide;
    private boolean treeHiding;
    private int treeSeenTicks;
    // KNOCK: the door it knocks on
    @Nullable
    private BlockPos knockDoor;
    private static final int ATTACK_TICKS = 12, ATTACK_HIT_AT = 6;

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
    // A* plan (DigPathfinder): feet positions to walk through, re-planned as the player moves
    @Nullable
    private List<BlockPos> digPath;
    private int digPathIndex;
    private BlockPos digPathGoal = BlockPos.ORIGIN;
    private int digPathPlannedAt;

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

    /**
     * How hard it is watching its victim, for the eye on the victim's HUD: 0 = not watching; from 0.2 while it
     * stands and stares, up to 1 when it is about to come for you (you stared too long, or came too close).
     */
    public float watchLevel() {
        Mode m = getMode();
        if (m == Mode.TREE || m == Mode.KNOCK || m == Mode.PEEK) return 0.6f;
        if (!isSightingMode(m)) return 0f;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        double stare = lookedAtTicks / Math.max(1.0, cfg.stareTriggerSeconds * 20);
        double close = lastVictimDistance < 0 ? 0 : (32 - lastVictimDistance) / Math.max(1.0, 32 - cfg.chaseTriggerDistance);
        return MathHelper.clamp(0.2f + 0.8f * (float) Math.max(stare, close), 0f, 1f);
    }

    /** Who it is after (null = nobody yet). */
    @Nullable
    public UUID getVictimUuid() {
        return victim;
    }

    /** Ticks spent in the current mode (debug). */
    public int modeTicks() {
        return modeTicks;
    }

    public boolean isForced() {
        return forced;
    }

    /** Commands and spawn eggs: the It always does its full routine (chase, sink, jumpscare). */
    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public long lastTickTime() {
        return lastTickTime;
    }

    @Override
    public net.minecraft.entity.EntityData initialize(net.minecraft.world.ServerWorldAccess world, net.minecraft.world.LocalDifficulty difficulty,
                                                      net.minecraft.entity.SpawnReason spawnReason, @Nullable net.minecraft.entity.EntityData entityData) {
        if (spawnReason == net.minecraft.entity.SpawnReason.SPAWN_EGG || spawnReason == net.minecraft.entity.SpawnReason.COMMAND) forced = true;
        return super.initialize(world, difficulty, spawnReason, entityData);
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
        if (knockDoor != null) nbt.putLong("KnockDoor", knockDoor.asLong());
        nbt.putBoolean("Forced", forced);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(MODE, nbt.getInt("Mode"));
        setNoGravity(getMode() == Mode.DIG);
        if (nbt.containsUuid("Victim")) victim = nbt.getUuid("Victim");
        if (nbt.contains("KnockDoor")) knockDoor = BlockPos.fromLong(nbt.getLong("KnockDoor"));
        forced = nbt.getBoolean("Forced");
        // short-lived tricks do not survive a reload: it simply leaves
        Mode m = getMode();
        if (m == Mode.FEINT || m == Mode.TREE || m == Mode.ATTACK || m == Mode.MOVE_UNSEEN) {
            this.dataTracker.set(MODE, (m == Mode.ATTACK ? Mode.CHASE : m == Mode.MOVE_UNSEEN ? Mode.STARE : Mode.DISAPPEAR).ordinal());
        }
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

    /** Turns this It into the body of the god of the session (see com.itwastall.god.ItForm). */
    public void makePuppetOf(UUID god) {
        this.puppetOwner = god;
        setAiDisabled(true);
        setInvulnerable(true);
        setNoGravity(true);
    }

    @Nullable
    public UUID puppetOwner() {
        return puppetOwner;
    }

    /** Mode and its timer set from outside (the god's body). */
    public void setPuppetMode(Mode mode) {
        setMode(mode);
    }

    @Override
    public boolean shouldSave() {
        return puppetOwner == null && super.shouldSave();   // the god's body never stays in the world
    }

    @Override
    protected void mobTick() {
        if (puppetOwner != null) return;
        super.mobTick();
        modeTicks++;
        if (attackCooldown > 0) attackCooldown--;
        if (getWorld() instanceof ServerWorld sw && (!slotClaimed || this.age % 20 == 0)) {
            if (!claimSlot(sw)) {
                discard();   // another It already owns this server (MODPLAN §4: no duplicates)
                return;
            }
        }

        // (FAZA PRE no longer freezes an existing It: natural spawns never happen before IT JOINED anyway,
        //  and an It from a command or a spawn egg must behave normally — it used to stand frozen.)
        if (getWorld() instanceof ServerWorld sw) {
            lastTickTime = sw.getServer().getOverworld().getTime();
            // nobody within 128 blocks: it has no one to haunt, and it must not block the server limit
            if (this.age > 40 && sw.getClosestPlayer(this, 128) == null) {
                discard();
                return;
            }
        }

        PlayerEntity player = findVictim();
        if (player == null && isSightingMode(getMode())) setMode(Mode.IDLE);
        // IT SPEAKS: now and then, a few words out of the dark while it watches you (in the mines: about digging)
        if (getWorld() instanceof ServerWorld vw && player != null && --voiceCooldown <= 0
                && (isSightingMode(getMode()) || getMode() == Mode.DIG || getMode() == Mode.PEEK)) {
            voiceCooldown = 20 * (18 + random.nextInt(25));
            boolean cave = getMode() == Mode.DIG || getMode() == Mode.PEEK;
            ItVoice.say(vw, getEyePos(), cave ? com.itwastall.registry.ModVoices.IT_CAVE : com.itwastall.registry.ModVoices.IT_ANYWHERE, 1.4f);
        }
        switch (getMode()) {
            case IDLE -> tickIdle(player);
            case STARE -> tickStare(player);
            case STALK -> tickStalk(player);
            case WATCHED -> tickWatched(player);
            case MOVE_UNSEEN -> tickMoveUnseen(player);
            case TREE -> tickTree(player);
            case FEINT -> tickFeint(player);
            case KNOCK -> tickKnock(player);
            case CHASE -> tickChase(player);
            case ATTACK -> tickAttack(player);
            case SINK -> tickSink(player);
            case DIG -> tickDig(player);
            case PEEK -> tickPeek(player);
            case EMERGE -> tickEmerge(player);
            case DISAPPEAR -> tickDisappear(player);
        }
    }

    private static boolean isSightingMode(Mode mode) {
        return mode == Mode.STARE || mode == Mode.STALK || mode == Mode.WATCHED || mode == Mode.MOVE_UNSEEN;
    }

    /**
     * MODPLAN §4: the one real It is written into the world data every second. If the record belongs to
     * another It that still counts (it ticked recently, loaded or not), this one is the duplicate.
     */
    private boolean claimSlot(ServerWorld sw) {
        ItWasTallState state = ItWasTallState.getOrCreate(sw.getServer());
        long now = sw.getServer().getOverworld().getTime();
        UUID owner = state.activeIt();
        if (owner != null && !owner.equals(getUuid()) && state.activeItFresh(now)
                && Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts) <= 1) {
            return false;
        }
        state.noteActiveIt(getUuid(), sw.getRegistryKey().getValue().toString(), getBlockPos().asLong(), now);
        slotClaimed = true;
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // killed or discarded: the slot is free again. Unloaded / changed dimension: it still exists.
        if (reason.shouldDestroy() && getWorld() instanceof ServerWorld sw) {
            ItWasTallState.getOrCreate(sw.getServer()).clearActiveIt(getUuid());
        }
    }

    // --------------------------------------------------------------- IDLE ----

    private void tickIdle(@Nullable PlayerEntity player) {
        getNavigation().stop();
        if (player != null) {
            setMode(Mode.STARE);
            return;
        }
        if (modeTicks > 200) discard();   // nobody around to see it leave
    }

    /**
     * Shared by STARE / STALK / WATCHED / MOVE_UNSEEN: the bookkeeping of a sighting and every way out of it.
     * Returns true when it already switched to another state (the caller must stop then).
     */
    private boolean tickSighting(PlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        Stage st = Director.currentStage(); // live per tick (PHASES §0) — no snapshot from init
        sightTicks++;
        // Multiplayer: anyone else who walks right up to it gets chased instead.
        PlayerEntity intruder = getWorld().getClosestPlayer(getX(), getY(), getZ(), cfg.chaseTriggerDistance,
                p -> EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(p) && p != player);
        if (intruder != null && !ShelterCheck.isSheltered(getWorld(), intruder.getBlockPos())) {
            maybeStartChase(intruder);
            return true;
        }
        faceTowards(player);

        double dist = distanceTo(player);
        watchedNow = isBeingWatchedBy(player);
        lookedAtTicks = watchedNow ? lookedAtTicks + 1 : Math.max(0, lookedAtTicks - 2);
        unwatchedTicks = watchedNow ? 0 : unwatchedTicks + 1;
        boolean approaching = lastVictimDistance > 0 && dist < lastVictimDistance - 0.02;
        lastVictimDistance = dist;

        if (sightTicks == 2 && player instanceof ServerPlayerEntity sp) {
            MineEvents.playTo(sp, ModSounds.AMBIENT_STING, getEyePos(), 1.2f, 1.0f);
        }
        if (dist < 24 && sightTicks % 90 == 45) {
            getWorld().playSound(null, getBlockPos(), ModSounds.GROWL, getSoundCategory(), ModSounds.vol(0.9f), 0.8f + random.nextFloat() * 0.2f);
        }
        if (sightTicks % STARE_SOUND_INTERVAL == 1) {
            playSoundTo(player, ModSounds.STARE, 0.6f, 0.8f + random.nextFloat() * 0.2f);
        }

        maybeAwardWatchTension(player); // PHASES §2: looked at >= 2 s -> +1.0 (30 min cap per player)

        boolean sheltered = ShelterCheck.isSheltered(getWorld(), player.getBlockPos());
        boolean tooClose = dist < cfg.chaseTriggerDistance;
        boolean staredDown = lookedAtTicks > cfg.stareTriggerSeconds * 20 && approaching;
        if ((tooClose || staredDown) && !sheltered) {
            maybeStartChase(player); // stage-gated: chase may simply not happen (see helper)
            return true;
        }
        // FEINT (stage 3+): you have been looking at it for a second and a half, far enough away for a run-up
        if (!feintRolled && lookedAtTicks == 30 && st.ordinal() >= 3 && cfg.feints && !sheltered
                && dist > 10 && dist < 34 && getNavigation().findPathTo(player, 0) != null) {
            feintRolled = true;
            if (random.nextFloat() < MathHelper.clamp(cfg.feintChance, 0.0, 1.0)) {
                startFeint(player);
                return true;
            }
        }
        if (sightTicks > cfg.stareTimeoutSeconds * 20) {
            startDisappear();
            return true;
        }
        return false;
    }

    /** STALK conditions: from Stage 2 onward It slowly closes the distance while unseen. */
    private boolean canStalk(PlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        double dist = distanceTo(player);
        return Director.currentStage().ordinal() >= 2 && cfg.stalking && !watchedNow
                && !ShelterCheck.isSheltered(getWorld(), player.getBlockPos())
                && dist > Math.max(2.0, cfg.stalkStopDistance)
                && dist <= Math.max(cfg.stalkStopDistance, cfg.stalkMaxDistance)
                && canSee(player);
    }

    private void freeze() {
        // Cancel residual horizontal motion too: being spotted must freeze It immediately.
        getNavigation().stop();
        setVelocity(0.0, getVelocity().y, 0.0);
    }

    private void tickStare(@Nullable PlayerEntity player) {
        if (player == null || tickSighting(player)) return;
        freeze();
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (watchedNow) {
            setMode(Mode.WATCHED);
            if (player instanceof ServerPlayerEntity sp) com.itwastall.advancement.Achievements.grant(sp, "first_sighting");
        } else if (canStalk(player)) {
            setMode(Mode.STALK);
        } else if (Director.currentStage().ordinal() >= 2 && cfg.moveUnseen && distanceTo(player) > 22
                && !ShelterCheck.isSheltered(getWorld(), player.getBlockPos())
                && sightTicks % 100 == 50 && random.nextFloat() < 0.35f) {
            setMode(Mode.MOVE_UNSEEN);
        }
    }

    private void tickStalk(@Nullable PlayerEntity player) {
        if (player == null || tickSighting(player)) return;
        if (watchedNow) {
            freeze();
            setMode(Mode.WATCHED);
            return;
        }
        if (!canStalk(player)) {
            freeze();
            setMode(Mode.STARE);
            return;
        }
        double stalkSpeed = MathHelper.clamp(ItWasTallConfig.INSTANCE.stalkSpeed, 0.01, 0.35);
        if (modeTicks % 10 == 1 || getNavigation().isIdle()) {
            getNavigation().startMovingTo(player, stalkSpeed);
        }
    }

    private void tickWatched(@Nullable PlayerEntity player) {
        if (player == null || tickSighting(player)) return;
        freeze();
        if (unwatchedTicks >= 10) setMode(Mode.STARE);   // you looked away for half a second
    }

    private void tickMoveUnseen(@Nullable PlayerEntity player) {
        if (player == null || tickSighting(player)) return;
        freeze();
        if (watchedNow) {
            setMode(Mode.WATCHED);
            return;
        }
        // MOVE_UNSEEN: far away and not looked at -> it is simply somewhere closer next time you look
        if (tryRepositionUnseen(player, distanceTo(player)) || modeTicks > 40) setMode(Mode.STARE);
    }

    // -------------------------------------------------------------- DISAPPEAR ----

    /** It leaves: as soon as nobody is looking at it (or after a few seconds at most). */
    public void startDisappear() {
        if (getMode() == Mode.DISAPPEAR) return;
        setTarget(null);
        freeze();
        setMode(Mode.DISAPPEAR);
    }

    private void tickDisappear(@Nullable PlayerEntity player) {
        freeze();
        if (player != null) faceTowards(player);
        if (player == null || !visibleTo(player, getPos().add(0, 2.5, 0)) || modeTicks > 60) {
            if (player instanceof ServerPlayerEntity sp && modeTicks <= 60) com.itwastall.advancement.Achievements.grant(sp, "dont_blink");
            discard();
        }
    }

    // ------------------------------------------------------------------ FEINT ----

    /** Test entry (/itwastall event feint). */
    public void beginFeint(PlayerEntity player) {
        startFeint(player);
    }

    /** A fake attack: a scream, a run at you, and a few blocks away it is just not there anymore. */
    private void startFeint(PlayerEntity player) {
        setVictim(player);
        setMode(Mode.FEINT);
        getWorld().playSound(null, getBlockPos(), ModSounds.SCREAM, getSoundCategory(), ModSounds.vol(2.2f), 1.05f);
        if (player instanceof ServerPlayerEntity sp) ModNetworking.sendChase(sp, true);
    }

    private void tickFeint(@Nullable PlayerEntity player) {
        if (player == null) {
            discard();
            return;
        }
        faceTowards(player);
        if (modeTicks % 5 == 1 || getNavigation().isIdle()) {
            getNavigation().startMovingTo(player, ItWasTallConfig.INSTANCE.chaseSpeed * 1.1);
        }
        if (distanceTo(player) < 2.8 || modeTicks > 70) {
            if (getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.6), getZ(), 24, 0.3, 0.9, 0.3, 0.01);
            }
            if (player instanceof ServerPlayerEntity sp) {
                MineEvents.playTo(sp, ModSounds.AMBIENT_STING, sp.getEyePos(), 1.0f, 1.3f);
                ModNetworking.sendChase(sp, false);
                JournalLog.add(sp, "It ran straight at me. Then it just wasn't there.");
            }
            if (Director.phase() == 1) Director.addTension(2.0);
            discard();
        }
    }

    // ------------------------------------------------------------------- TREE ----

    /** Spawned half-hidden next to a trunk; {@code hide} is the spot right behind it. */
    public void startTree(Vec3d hide) {
        treeHide = hide;
        treeHiding = false;
        treeSeenTicks = 0;
        setMode(Mode.TREE);
    }

    private void tickTree(@Nullable PlayerEntity player) {
        if (player == null || treeHide == null) {
            startDisappear();
            return;
        }
        faceTowards(player);
        double dist = distanceTo(player);
        if (dist < ItWasTallConfig.INSTANCE.chaseTriggerDistance * 0.8 && !ShelterCheck.isSheltered(getWorld(), player.getBlockPos())) {
            maybeStartChase(player);
            return;
        }
        if (treeHiding) {
            // one slow step back behind the trunk, then it is gone
            getMoveControl().moveTo(treeHide.x, treeHide.y, treeHide.z, 0.6);
            if (getPos().squaredDistanceTo(treeHide.x, getY(), treeHide.z) < 0.09 || modeTicks > 50) startDisappear();
            return;
        }
        freeze();
        if (isBeingWatchedBy(player)) treeSeenTicks++;
        if (treeSeenTicks >= 12) {
            treeHiding = true;
            modeTicks = 0;
            getWorld().playSound(null, getBlockPos(), ModSounds.STEP, getSoundCategory(), ModSounds.vol(0.5f), 0.5f);
            maybeAwardWatchTension(player);
        } else if (modeTicks > 20 * 60) {
            startDisappear();
        }
    }

    // ------------------------------------------------------------------ KNOCK ----

    /** Stage 5: stands outside the door at {@code door} (lower half) and knocks. */
    public void startKnock(BlockPos door) {
        knockDoor = door.toImmutable();
        setMode(Mode.KNOCK);
    }

    private void tickKnock(@Nullable PlayerEntity player) {
        freeze();
        if (player == null || knockDoor == null) {
            startDisappear();
            return;
        }
        BlockState door = getWorld().getBlockState(knockDoor);
        Vec3d at = Vec3d.ofCenter(knockDoor).add(0, 0.4, 0);
        double dx = at.x - getX(), dz = at.z - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        boolean doorGone = !(door.getBlock() instanceof net.minecraft.block.DoorBlock);
        boolean opened = !doorGone && door.get(net.minecraft.block.DoorBlock.OPEN);
        if (opened || doorGone) {
            // you opened it. It was waiting for that.
            if (distanceTo(player) < 16) {
                if (Director.currentStage().chaseEnabled()) startChase(player);
                else startDisappear();
            } else {
                startDisappear();
            }
            return;
        }
        // three bursts of three knocks
        int t = modeTicks - 20;
        if (t >= 0 && t < 3 * 60 && t % 60 < 30 && t % 60 % 10 == 0) {
            if (t % 60 == 0) triggerAnim("action", "knock");
            if (player instanceof ServerPlayerEntity sp) {
                MineEvents.playTo(sp, ModSounds.SCARE_KNOCK, at, 1.2f, 0.8f + random.nextFloat() * 0.1f);
            }
        }
        if (modeTicks > 20 + 3 * 60 + 60) startDisappear();
    }

    /**
     * Stage-gated chase trigger (PHASES §3 row "CHASE"): while chase is not enabled (ETAP 0-2, and
     * ETAP 1-2 always), or when the stage's chance roll fails (ETAP 3 keeps it optional at 35%),
     * It instead quietly disappears — no scream, no jumpscare. The roll happens once, exactly when
     * the trigger conditions are first met, so a failing roll means one sighting that walks away.
     */
    private void maybeStartChase(PlayerEntity player) {
        Stage st = Director.currentStage();
        if (forced) {                       // command / spawn egg: always the real thing
            startChase(player);
            return;
        }
        if (!st.chaseEnabled() || this.random.nextFloat() >= st.chaseChance()) {
            disappearQuiet();
            return;
        }
        startChase(player);
    }

    /** Quiet disappearance: no sound, no effect, no jumpscare ("It znika", PHASES Etapy notes) —
     *  as soon as it is out of your sight (DISAPPEAR state). */
    private void disappearQuiet() {
        startDisappear();
    }

    /** Ends an encounter where it had to give up: SINK when the stage allows it, quiet otherwise. */
    private void finishEncounter() {
        if (forced || Director.currentStage().sinkEnabled()) startSink();
        else disappearQuiet();
    }

    private void startChase(PlayerEntity player) {
        setVictim(player);
        setNoGravity(false);
        clearDig();
        setMode(Mode.CHASE);
        setTarget(player);
        getWorld().playSound(null, getBlockPos(), ModSounds.SCREAM, getSoundCategory(), ModSounds.vol(2.5f), 0.85f);
        if (getWorld() instanceof ServerWorld vw) ItVoice.say(vw, getEyePos(), com.itwastall.registry.ModVoices.IT_CHASE, 1.8f);
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
            if (player instanceof ServerPlayerEntity sp) com.itwastall.advancement.Achievements.grant(sp, "survive_chase");
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
            setMode(Mode.ATTACK);
        }
    }

    /** ATTACK: a short wind-up, one hit if you are still in reach, back to the chase. */
    private void tickAttack(@Nullable PlayerEntity player) {
        if (player == null) {
            startDisappear();
            return;
        }
        freeze();
        faceTowards(player);
        if (modeTicks == 1) {
            triggerAnim("action", "attack");
            playSound(ModSounds.ATTACK, ModSounds.vol(1.2f), 1.0f);
        }
        if (modeTicks == ATTACK_HIT_AT) {
            swingHand(getActiveHand());
            if (squaredDistanceTo(player) < 6.5) tryAttack(player);
        }
        if (modeTicks >= ATTACK_TICKS) {
            attackCooldown = 20 - ATTACK_TICKS;
            setMode(Mode.CHASE);
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
        getWorld().playSound(null, getBlockPos(), ModSounds.SINK, getSoundCategory(), ModSounds.vol(1.8f), 0.7f);
        if (escapedFromChase && Director.phase() == 1) Director.addTension(-3.0); // PHASES §2: udana ucieczka do bazy w pościgu -3
    }

    private void tickSink(@Nullable PlayerEntity player) {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (player != null && modeTicks < SINK_ANIM_TICKS) faceTowards(player);   // it stares at you as it goes down
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
                if (forced || Director.currentStage().jumpscareEnabled()) {
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

    /** Climbs out of the torn body of the Old One, then hunts the player who came too close. */
    public void startEmerge(PlayerEntity player) {
        setVictim(player);
        setNoGravity(false);
        clearDig();
        setMode(Mode.EMERGE);
        getNavigation().stop();
        setVelocity(Vec3d.ZERO);
    }

    private void tickEmerge(@Nullable PlayerEntity player) {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (player != null) faceTowards(player);
        if (modeTicks >= EMERGE_TICKS) {
            // the payoff of the Old One: it always runs at you, whatever the stage table says
            if (player != null) startChase(player);
            else setMode(Mode.STARE);
        }
    }

    public void startPeek() {
        getWorld().playSound(null, getBlockPos(), ModSounds.SNIFF, getSoundCategory(), ModSounds.vol(0.7f), 1.0f);
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
        digPath = null;
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
            getWorld().playSound(null, getBlockPos(), ModSounds.SCRAPE, getSoundCategory(), ModSounds.vol(0.8f), 0.9f);
        }
        if (!digCells.isEmpty() && digTicks % 6 == 1) {
            BlockPos c = digCells.get(random.nextInt(digCells.size()));
            BlockState st = getWorld().getBlockState(c);
            getWorld().playSound(null, c, st.getSoundGroup().getHitSound(), getSoundCategory(), ModSounds.vol(1.0f), 0.6f);
            getWorld().playSound(null, c, ModSounds.DIG, getSoundCategory(), ModSounds.vol(0.7f), 0.9f + random.nextFloat() * 0.2f);
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

        // 1) follow the A* plan (MODPLAN §13 DigPathfinder); re-plan every 10 s or when the player moved away
        boolean stale = digPath == null || digPathIndex >= digPath.size()
                || modeTicks - digPathPlannedAt > 200 || goal.getManhattanDistance(digPathGoal) > 4;
        if (stale) {
            digPath = DigPathfinder.find(getWorld(), feet, goal, TallOneEntity::breakTicks);
            digPathIndex = 0;
            digPathGoal = goal;
            digPathPlannedAt = modeTicks;
        }
        if (digPath != null && digPathIndex < digPath.size()) {
            BlockPos next = digPath.get(digPathIndex);
            Direction step = null;
            for (Direction d : Direction.values()) if (feet.offset(d).equals(next)) step = d;
            if (step != null && tryDigStep(feet, step)) {
                digPathIndex++;
                return true;
            }
            digPath = null;   // the world changed under the plan: fall back for this step, re-plan next time
        }

        // 2) fallback: the old greedy step straight towards the player
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
            if (tryDigStep(feet, dir)) return true;
        }
        return false;
    }

    /** Prepares one step: the blocks to break and how long it takes. False when the step is impossible. */
    private boolean tryDigStep(BlockPos feet, Direction dir) {
        if (DigPathfinder.stepCost(getWorld(), feet, dir, TallOneEntity::breakTicks) < 0) return false;
        int duration = 0;
        List<BlockPos> toBreak = new ArrayList<>();
        for (BlockPos c : cellsFor(feet, dir)) {
            BlockState st = getWorld().getBlockState(c);
            if (st.getCollisionShape(getWorld(), c).isEmpty()) continue;
            toBreak.add(c);
            duration = Math.max(duration, breakTicks(st.getHardness(getWorld(), c)));
        }
        digNext = feet.offset(dir);
        digCells.clear();
        digCells.addAll(toBreak);
        digDuration = Math.max(1, duration);
        digTicks = 0;
        return true;
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

    // ----------------------------------------------------------- MOVE_UNSEEN ----

    /**
     * Moves to a spot closer to the player (65 % of the distance, at least 14 blocks away) on the
     * same side, but only if NEITHER the old nor the new position is visible to the player right
     * now — it never teleports in front of anyone (MODPLAN §3.1).
     */
    private boolean tryRepositionUnseen(PlayerEntity player, double dist) {
        World world = getWorld();
        if (visibleTo(player, getPos().add(0, 2.5, 0))) return false;
        double baseAng = Math.atan2(getZ() - player.getZ(), getX() - player.getX());
        double newDist = Math.max(14.0, dist * 0.65);
        for (int i = 0; i < 12; i++) {
            double ang = baseAng + (random.nextDouble() - 0.5) * 1.4;
            double x = player.getX() + Math.cos(ang) * newDist;
            double z = player.getZ() + Math.sin(ang) * newDist;
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
            if (Math.abs(y - player.getY()) > 12) continue;
            BlockPos below = BlockPos.ofFloored(x, y - 1, z);
            BlockState ground = world.getBlockState(below);
            if (!ground.getFluidState().isEmpty() || ground.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) continue;
            Vec3d target = new Vec3d(x, y, z);
            if (visibleTo(player, target.add(0, 2.5, 0))) continue;
            if (!world.isSpaceEmpty(this, getBoundingBox().offset(target.subtract(getPos())))) continue;
            refreshPositionAndAngles(x, y, z, getYaw(), getPitch());
            getNavigation().stop();
            return true;
        }
        return false;
    }

    /** Is this point in the player's view cone with a clear line of sight? */
    private boolean visibleTo(PlayerEntity player, Vec3d point) {
        Vec3d to = point.subtract(player.getEyePos());
        if (to.lengthSquared() < 1.0E-4) return true;
        if (player.getRotationVec(1f).dotProduct(to.normalize()) < 0.35) return false;   // outside the view cone
        var hit = getWorld().raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), point,
                net.minecraft.world.RaycastContext.ShapeType.VISUAL, net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
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
            playSound(ModSounds.ATTACK, ModSounds.vol(1.0f), 1.0f);
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
        if (getMode() == Mode.EMERGE || getMode() == Mode.FEINT || getMode() == Mode.DISAPPEAR) return false;
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
        playSound(ModSounds.STEP, ModSounds.vol(0.35f), 0.6f);
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
            case EMERGE -> state.setAndContinue(EMERGE);
            case CHASE, FEINT -> state.setAndContinue(state.isMoving() ? RUN : IDLE);
            case STALK -> state.setAndContinue(WALK);
            case ATTACK, KNOCK, WATCHED, MOVE_UNSEEN, DISAPPEAR, IDLE -> state.setAndContinue(IDLE);
            case STARE, TREE -> state.setAndContinue(state.isMoving() ? WALK : IDLE);
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
