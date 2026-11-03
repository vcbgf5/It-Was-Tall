package com.itwastall.client.render;

import com.itwastall.entity.TallOneEntity;
import com.itwastall.net.FalsePresencePayload;
import com.itwastall.registry.ModEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * FALSE PRESENCE renderer (MODPLAN §7.4): the figure is a {@link TallOneEntity} instance that
 * exists only in THIS one client's world, created from the server's {@link FalsePresencePayload}.
 * It has no server presence at all — no hitbox, no damage, does not appear on any server list
 * and does not count against the one true Tall One per server (MODPLAN §4).
 *
 * <p>Behaviour (PHASES §7.2): stands still,Idles; disappears the moment the player looks
 * straight at it ("znikniecie po spojrzeniu") or when its short lifetime runs out. Window
 * figures (behind the glass) stay put until their lifetime ends — they are meant to be seen.
 */
public final class FalsePresenceRenderer {
    private static final Map<Integer, Sighting> ACTIVE = new HashMap<>();
    /** Where all current sightings live; a world/dimension change clears everything. */
    private static ClientWorld trackedWorld;

    /** Per-sighting state;mutable alongside ticks — a plain class, not a record. */
    private static final class Sighting {
        final int id;
        final boolean window;
        int ticksLeft;

        Sighting(int id, int ticksLeft, boolean window) {
            this.id = id;
            this.ticksLeft = ticksLeft;
            this.window = window;
        }
    }

    private FalsePresenceRenderer() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FalsePresenceRenderer::tick);
    }

    /** Entry point from the payload receiver. */
    public static void show(double x, double y, double z, float yaw, int durationTicks, boolean window) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || durationTicks <= 0) return;

        TallOneEntity fake = new TallOneEntity(ModEntities.TALL_ONE, world);
        fake.refreshPositionAndAngles(x, y, z, yaw, 0f);
        int id = nextEntityId();
        // a fake never chases: client-side entities never run the server-only state machine,
        // so MODE stays STARE (pure standing + the head tracking look control) — exactly the
        // "moze stac nieruchomo / wygladac jak prawdziwy The Tall One" intent.
        fake.setPersistent(); // keep it from any despawn logic anything else might run on it
        fake.setId(id);       // private id range: never collides with real spawned entities
        // Lower the window silhouette so its head, not its torso, lines up with the glass.
        if (window) fake.setPosition(x, y - 2.5, z);
        world.addEntity(fake);
        if (window) {
            // Start after insertion so GeckoLib has a live client-side entity to animate.
            fake.playWindowKnockAnimation();
            if (client.player != null) facePlayer(fake, client.player);
        }
        ACTIVE.put(id, new Sighting(id, durationTicks, window));
        trackedWorld = world;
    }

    /** Unique ids in a private range so real server entities can never collide with these. */
    private static int counter = 1_900_000;

    private static int nextEntityId() {
        return counter++;
    }

    /** One client tick: lifetime countdown + "vanishes when looked at" + world-change cleanup. */
    private static void tick(MinecraftClient client) {
        if (ACTIVE.isEmpty()) return;
        ClientWorld world = client.world;
        PlayerEntity player = client.player;
        if (world == null || player == null || world != trackedWorld) {
            clearAll();
            return;
        }

        Iterator<Sighting> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Sighting sighting = it.next();
            TallOneEntity fake = (TallOneEntity) world.getEntityById(sighting.id);
            if (fake == null || fake.isRemoved()) {
                it.remove();
                continue;
            }
            boolean lookedAt = false;
            if (sighting.window) {
                // Keep the body and head aimed at the player as they move inside the room.
                facePlayer(fake, player);
            } else {
                double gross = player.squaredDistanceTo(fake);
                if (gross < 48.0 * 48.0 && isLookingAt(player, fake)) {
                    lookedAt = true;
                }
            }
            if (lookedAt || --sighting.ticksLeft <= 0) {
            world.removeEntity(sighting.id, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            it.remove();
        }
        }
    }

    private static void facePlayer(TallOneEntity entity, PlayerEntity player) {
        double dx = player.getX() - entity.getX();
        double dz = player.getZ() - entity.getZ();
        float yaw = (float) (net.minecraft.util.math.MathHelper.atan2(dz, dx)
                * net.minecraft.util.math.MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        entity.setYaw(yaw);
        entity.setBodyYaw(yaw);
        entity.setHeadYaw(yaw);
    }

    /** Same idea as the real entity's isBeingWatchedBy: a vanish must correspond to something
     *  the player can actually see — the view-frustum cone plus a real line of sight. */
    private static boolean isLookingAt(PlayerEntity player, TallOneEntity fake) {
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d toIt = new Vec3d(fake.getX() - player.getX(),
                fake.getBodyY(0.75) - player.getEyeY(),
                fake.getZ() - player.getZ());
        double dist = toIt.length();
        double dot = look.dotProduct(toIt.normalize());
        // slightly stricter than the real entity's cone so the figure does not keep popping
        // in and out when glancing around in the periphery
        return dot > 1.0 - 0.06 / Math.max(1.0, dist / 8.0) && player.canSee(fake);
    }

    private static void clearAll() {
        ClientWorld world = trackedWorld;
        if (world != null) {
            for (Sighting sighting : ACTIVE.values()) {
                if (world.getEntityById(sighting.id) != null) {
                    world.removeEntity(sighting.id, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                }
            }
        }
        ACTIVE.clear();
    }
}
