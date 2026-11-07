package com.itwastall.god;

import com.itwastall.ItWasTall;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.net.GodFormPayload;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The god PLAYS IT. Emerge out of the ground where you look; you are as tall as It (scale), fast, and the other
 * players see It walking — a body that mirrors your every move (the god stays hidden; the body is hidden from
 * the god's own client). Look at a player for a moment and they are yours: outlined for you alone, and their
 * screen turns red, the heartbeat starts — the chase is on. Hit them to catch them (a jumpscare and a hard
 * blow). Sink back into the ground when you are done and fly off unseen.
 */
public final class ItForm {
    private static final Identifier SCALE_ID = ItWasTall.id("god_it_scale");
    private static final Identifier SPEED_ID = ItWasTall.id("god_it_speed");
    private static final int EMERGE_TICKS = 32, SINK_TICKS = 30, ATTACK_TICKS = 12;

    private static final class Form {
        TallOneEntity body;
        @Nullable UUID victim;
        int emergeLeft = EMERGE_TICKS;
        int attackLeft;
        int sinkLeft = -1;            // >= 0 while the body sinks into the ground
    }

    private static final Map<UUID, Form> FORMS = new HashMap<>();

    private ItForm() {}

    public static boolean active(ServerPlayerEntity god) {
        Form f = FORMS.get(god.getUuid());
        return f != null && f.sinkLeft < 0;
    }

    public static boolean isBodyOf(TallOneEntity it, UUID player) {
        return player.equals(it.puppetOwner());
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(GodFormPayload.ID, GodFormPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(ItForm::tick);
        // a hit from It catches the player: jumpscare and a heavy blow (the god's own hit never lands)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity god) || !active(god)) return ActionResult.PASS;
            if (entity instanceof ServerPlayerEntity victim && !GodMode.isGod(victim.getUuid())) {
                if (god.distanceTo(victim) <= 4.5) catchVictim(god, victim);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    /** H / panel: emerge where you look, or sink back into the ground. */
    public static void toggle(ServerPlayerEntity god) {
        Form f = FORMS.get(god.getUuid());
        if (f == null) emerge(god);
        else if (f.sinkLeft < 0) sink(god);
    }

    // ---------------------------------------------------------------- emerge / sink

    private static void emerge(ServerPlayerEntity god) {
        ServerWorld world = god.getServerWorld();
        BlockPos at = groundWhereLooking(god);
        if (at == null) {
            god.sendMessage(Text.literal("No ground there to rise from.").formatted(Formatting.GRAY), true);
            return;
        }
        TallOneEntity body = ModEntities.TALL_ONE.create(world);
        if (body == null) return;
        float yaw = god.getYaw();
        body.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        body.makePuppetOf(god.getUuid());
        body.setPuppetMode(TallOneEntity.Mode.EMERGE);
        world.spawnEntity(body);
        Form f = new Form();
        f.body = body;
        FORMS.put(god.getUuid(), f);

        god.teleport(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, god.getPitch());
        god.getAbilities().flying = false;
        god.getAbilities().allowFlying = false;
        god.sendAbilitiesUpdate();
        addModifier(god.getAttributeInstance(EntityAttributes.GENERIC_SCALE),
                new EntityAttributeModifier(SCALE_ID, 0.9, EntityAttributeModifier.Operation.ADD_VALUE));
        addModifier(god.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                new EntityAttributeModifier(SPEED_ID, 0.35, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        world.playSound(null, at, ModSounds.DIG, SoundCategory.HOSTILE, ModSounds.vol(1.6f), 0.6f);
        sendState(god, f);
        god.sendMessage(Text.literal("You are It. Look at a player to hunt them. Hit to catch. H to sink.").formatted(Formatting.DARK_RED), true);
    }

    private static void sink(ServerPlayerEntity god) {
        Form f = FORMS.get(god.getUuid());
        if (f == null) return;
        f.sinkLeft = SINK_TICKS;
        f.victim = null;
        if (f.body.isAlive()) {
            f.body.setVictim(null);
            f.body.setPuppetMode(TallOneEntity.Mode.SINK);
            god.getServerWorld().playSound(null, f.body.getBlockPos(), ModSounds.SINK, SoundCategory.HOSTILE, ModSounds.vol(1.4f), 1.0f);
        }
        restorePlayer(god);
        sendState(god, null);
    }

    /** Ends It form at once (god mode switched off, the god left the game). */
    public static void end(UUID god, @Nullable ServerPlayerEntity player) {
        Form f = FORMS.remove(god);
        if (f != null && f.body != null) f.body.discard();
        if (player != null) {
            restorePlayer(player);
            sendState(player, null);
        }
    }

    private static void restorePlayer(ServerPlayerEntity god) {
        removeModifier(god.getAttributeInstance(EntityAttributes.GENERIC_SCALE), SCALE_ID);
        removeModifier(god.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED), SPEED_ID);
        if (god.isCreative()) {
            god.getAbilities().allowFlying = true;
            god.sendAbilitiesUpdate();
        }
    }

    // ---------------------------------------------------------------- hunting

    /** The god's client: you have been looking at this player for a moment. */
    public static void lock(ServerPlayerEntity god, String name) {
        Form f = FORMS.get(god.getUuid());
        if (f == null || f.sinkLeft >= 0 || god.getServer() == null) return;
        ServerPlayerEntity victim = god.getServer().getPlayerManager().getPlayer(name);
        if (victim == null || victim == god || GodMode.isGod(victim.getUuid())) return;
        if (victim.getWorld() != god.getWorld() || victim.distanceTo(god) > 96) return;
        if (victim.getUuid().equals(f.victim)) return;
        f.victim = victim.getUuid();
        f.body.setVictim(victim);
        f.body.setPuppetMode(TallOneEntity.Mode.CHASE);
        god.getServerWorld().playSound(null, f.body.getBlockPos(), ModSounds.SCREAM, SoundCategory.HOSTILE, ModSounds.vol(2.5f), 0.85f);
        ModNetworking.sendChase(victim, true);
        com.itwastall.entity.ItVoice.say(god.getServerWorld(), f.body.getEyePos(), com.itwastall.registry.ModVoices.IT_CHASE, 1.8f);
        JournalLog.add(victim, "It looked straight at me. Then it came.");
        sendState(god, f);
    }

    private static void catchVictim(ServerPlayerEntity god, ServerPlayerEntity victim) {
        Form f = FORMS.get(god.getUuid());
        if (f == null) return;
        f.attackLeft = ATTACK_TICKS;
        f.body.setPuppetMode(TallOneEntity.Mode.ATTACK);
        f.body.triggerAnim("action", "attack");
        ServerWorld world = god.getServerWorld();
        world.playSound(null, f.body.getBlockPos(), ModSounds.ATTACK, SoundCategory.HOSTILE, ModSounds.vol(1.6f), 0.9f);
        victim.damage(world.getDamageSources().mobAttack(f.body), 8f);
        ModNetworking.sendJumpscare(victim);
        JournalLog.add(victim, "It caught me.");
    }

    // ---------------------------------------------------------------- every tick

    private static void tick(MinecraftServer server) {
        if (FORMS.isEmpty()) return;
        for (Map.Entry<UUID, Form> e : List.copyOf(FORMS.entrySet())) {
            UUID id = e.getKey();
            Form f = e.getValue();
            ServerPlayerEntity god = server.getPlayerManager().getPlayer(id);
            if (god == null || !GodMode.isGod(id)) {
                end(id, god);
                continue;
            }
            if (f.sinkLeft >= 0) {
                if (--f.sinkLeft <= 0) {
                    FORMS.remove(id);
                    if (f.body.isAlive()) f.body.discard();
                }
                continue;
            }
            if (!f.body.isAlive() || f.body.getWorld() != god.getWorld()) {   // the body was removed (or the god left the world)
                end(id, god);
                continue;
            }
            // the body mirrors the god
            f.body.refreshPositionAndAngles(god.getX(), god.getY(), god.getZ(), god.getYaw(), god.getPitch());
            f.body.setBodyYaw(god.getBodyYaw());
            f.body.setHeadYaw(god.getHeadYaw());
            ServerPlayerEntity victim = f.victim == null ? null : server.getPlayerManager().getPlayer(f.victim);
            if (f.victim != null && (victim == null || victim.getWorld() != god.getWorld() || victim.distanceTo(god) > 128 || !victim.isAlive())) {
                f.victim = null;
                f.body.setVictim(null);
                victim = null;
                sendState(god, f);
            }
            TallOneEntity.Mode mode;
            if (f.emergeLeft > 0) {
                f.emergeLeft--;
                mode = TallOneEntity.Mode.EMERGE;
            } else if (f.attackLeft > 0) {
                f.attackLeft--;
                mode = TallOneEntity.Mode.ATTACK;
            } else {
                mode = victim != null ? TallOneEntity.Mode.CHASE : TallOneEntity.Mode.STARE;
            }
            if (f.body.getMode() != mode) f.body.setPuppetMode(mode);
            if (victim != null && server.getTicks() % 100 == 0) {
                com.itwastall.entity.MineEvents.playTo(victim, ModSounds.AMBIENT_HEARTBEAT, victim.getPos(), 1.0f, 1.0f);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Solid ground at the block the god looks at (up to 64 blocks), or under the god. */
    @Nullable
    private static BlockPos groundWhereLooking(ServerPlayerEntity god) {
        ServerWorld world = god.getServerWorld();
        Vec3d eye = god.getEyePos();
        Vec3d end = eye.add(god.getRotationVec(1f).multiply(64));
        BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, god));
        BlockPos start;
        if (hit.getType() == HitResult.Type.BLOCK) {
            start = hit.getSide() == Direction.UP ? hit.getBlockPos().up() : hit.getBlockPos().offset(hit.getSide());
        } else {
            BlockPos feet = god.getBlockPos();
            start = new BlockPos(feet.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()), feet.getZ());
        }
        BlockPos.Mutable p = start.mutableCopy();
        for (int i = 0; i < 24; i++) {
            BlockPos below = p.down();
            if (!world.getBlockState(below).getCollisionShape(world, below).isEmpty()) return p.toImmutable();
            if (p.getY() <= world.getBottomY()) return null;
            p.move(0, -1, 0);
        }
        return null;
    }

    private static void addModifier(@Nullable EntityAttributeInstance inst, EntityAttributeModifier mod) {
        if (inst == null) return;
        inst.removeModifier(mod.id());
        inst.addTemporaryModifier(mod);
    }

    private static void removeModifier(@Nullable EntityAttributeInstance inst, Identifier id) {
        if (inst != null) inst.removeModifier(id);
    }

    private static void sendState(ServerPlayerEntity god, @Nullable Form f) {
        if (!ServerPlayNetworking.canSend(god, GodFormPayload.ID)) return;
        int locked = -1;
        if (f != null && f.victim != null && god.getServer() != null) {
            ServerPlayerEntity v = god.getServer().getPlayerManager().getPlayer(f.victim);
            if (v != null) locked = v.getId();
        }
        ServerPlayNetworking.send(god, new GodFormPayload(f != null && f.sinkLeft < 0, locked));
    }
}
