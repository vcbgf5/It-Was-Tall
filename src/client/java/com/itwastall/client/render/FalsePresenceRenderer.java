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
        int ticksLeft;
        final boolean window;
        /** §15.4 NIE PATRZ watcher: turning around at it fails the task (reported once). */
        final boolean dontlook;
        /** Window sightings report one deliberate look-back to the server (one task each). */
        boolean reported;

        Sighting(int id, int ticksLeft, boolean window, boolean dontlook) {
            this.id = id;
            this.ticksLeft = ticksLeft;
            this.window = window;
            this.dontlook = dontlook;
        }
    }

    private FalsePresenceRenderer() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FalsePresenceRenderer::tick);
    }

    /** Entry point from the payload receiver. */
    public static void show(double x, double y, double z, float yaw, int durationTicks, boolean window) {
        show(x, y, z, yaw, durationTicks, window, false);
    }

    /** Full variant: the §15.4 NIE PATRZ watcher arrives with {@code dontlook = true}. */
    public static void show(double x, double y, double z, float yaw, int durationTicks,
                            boolean window, boolean dontlook) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || durationTicks <= 0) return;

        TallOneEntity fake = new TallOneEntity(ModEntities.TALL_ONE, world);
        double feetY = y;
        if (window) {
            // Window figure: the payload y is the bottom of the pane's block. Lower the body so
            // its EYES sit on the middle of the pane (the legs disappear below the window sill,
            // which is all the player inside can see anyway). getStandingEyeHeight() is a
            // RELATIVE offset — the old getBodyY() call returned an absolute world height and
            // dropped the figure to y = -2.5, deep under the map.
            feetY = y + 0.5 - fake.getStandingEyeHeight();
        }
        // place it once, directly at the final spot (no interpolation slide on the first frame)
        fake.refreshPositionAndAngles(x, feetY, z, yaw, 0f);
        int id = nextEntityId();
        // a fake never chases: client-side entities never run the server-only state machine,
        // so MODE stays STARE (pure standing + the head tracking look control) — exactly the
        // "moze stac nieruchomo / wygladac jak prawdziwy The Tall One" intent.
        fake.setPersistent(); // keep it from any despawn logic anything else might run on it
        fake.setId(id);       // private id range: never collides with real spawned entities
        world.addEntity(fake);
        if (window || dontlook) {
            if (dontlook) {
                // the watcher was born BEHIND the player — introduce itself with a sting
                MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                                com.itwastall.registry.ModSounds.AMBIENT_STING, 1.0f, com.itwastall.registry.ModSounds.vol(0.8f)));
            }

            // Start after insertion so GeckoLib has a live client-side entity to animate
            // (window knock anim; the watcher just stands and tracks the player's head).
            if (window) fake.playWindowKnockAnimation();
            if (client.player != null) facePlayer(fake, client.player);
        }
        ACTIVE.put(id, new Sighting(id, durationTicks, window, dontlook));
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
                // Window figures are MEANT to be seen (they never vanish on look). What a
                // deliberate look-back does: the server learns you saw It — and hands you a
                // task ("torch outage"-style pair). Reported once per sighting.
                if (!sighting.reported && isLookingAt(player, fake)) {
                    sighting.reported = true;
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.itwastall.net.WindowSeenPayload.ID))
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.itwastall.net.WindowSeenPayload(false));
                }
            } else if (sighting.dontlook) {
                // §15.4: the watcher tracks you quietly (it observes, it does not knock) —
                // but the moment you rotate onto it, the failure report goes out and the
                // figure vanishes on the spot (nothing left to stare at after that).
                facePlayer(fake, player);
                if (isLookingAt(player, fake)) {
                    sighting.reported = true;
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.itwastall.net.WindowSeenPayload.ID))
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.itwastall.net.WindowSeenPayload(true));
                    lookedAt = true;   // remove locally alongside the report
                }
            } else {
                facePlayer(fake, player);      // every figure keeps its eyes on you, all the time
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
        return dot > 1.0 - 0.06 / Math.max(1.0, dist / 8.0) && seesThroughGlass(player, fake);
    }

    /**
     * Line of sight from the eyes to the figure that looks THROUGH glass and glass panes (the window knock is
     * seen through the window). Vanilla canSee() stops at glass, because glass has a collision shape.
     */
    private static boolean seesThroughGlass(PlayerEntity player, TallOneEntity fake) {
        Vec3d from = player.getEyePos();
        Vec3d[] targets = {fake.getEyePos(), new Vec3d(fake.getX(), fake.getBodyY(0.6), fake.getZ())};
        for (Vec3d to : targets) {
            Vec3d start = from;
            Vec3d dir = to.subtract(from).normalize();
            boolean clear = false;
            for (int pass = 0; pass < 5; pass++) {
                net.minecraft.util.hit.BlockHitResult hit = player.getWorld().raycast(new net.minecraft.world.RaycastContext(
                        start, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                    clear = true;
                    break;
                }
                net.minecraft.block.Block block = player.getWorld().getBlockState(hit.getBlockPos()).getBlock();
                if (!(block instanceof net.minecraft.block.TransparentBlock || block instanceof net.minecraft.block.PaneBlock)) break;
                // glass: carry on from just behind this block
                Vec3d next = hit.getPos().add(dir.multiply(0.05));
                net.minecraft.util.math.BlockPos bp = hit.getBlockPos();
                while (net.minecraft.util.math.BlockPos.ofFloored(next).equals(bp) && next.squaredDistanceTo(to) > 0.01) {
                    next = next.add(dir.multiply(0.1));
                }
                start = next;
            }
            if (clear) return true;
        }
        return false;
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
