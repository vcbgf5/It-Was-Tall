package com.itwastall.client.ending;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * THE RITUAL'S END, as a cutscene: the camera leaves the player (HUD hidden, the player stands still) and shows it in
 * three shots, in step with RitualFinale on the server (16.5 s):
 * <ol>
 *   <li>from the front and a little above: the fire, the chains growing to the point above it (slow push in);</li>
 *   <li>from the side: It rising into the sky, the camera rising and tilting up with it;</li>
 *   <li>from high above: the fire gone out, the red pentagram turning on the ground.</li>
 * </ol>
 * Shots blend smoothly into each other. Then the camera is given back (just before the ending screen).
 */
public final class CutsceneCamera {
    private static final float LENGTH = 16.5f;
    private static boolean active;
    private static long startMs;
    private static Vec3d fire = Vec3d.ZERO;
    private static boolean hudWas;

    private CutsceneCamera() {}

    public static boolean active() {
        return active;
    }

    public static void start(BlockPos firePos) {
        MinecraftClient c = MinecraftClient.getInstance();
        fire = Vec3d.ofCenter(firePos);
        startMs = Util.getMeasuringTimeMs();
        if (!active) hudWas = c.options.hudHidden;
        active = true;
        c.options.hudHidden = true;
    }

    public static void stop() {
        if (!active) return;
        active = false;
        MinecraftClient.getInstance().options.hudHidden = hudWas;
    }

    /** Seconds since the cutscene began (stops it when it is over). */
    private static float time() {
        float s = (Util.getMeasuringTimeMs() - startMs) / 1000f;
        if (s > LENGTH || MinecraftClient.getInstance().world == null) stop();
        return s;
    }

    /** Where It hangs: two blocks above the fire, rising from 5.5 s to 11.5 s (0.06 blocks a tick). */
    private static double itY(float s) {
        float rise = MathHelper.clamp(s - 5.5f, 0f, 6f) * 20 * 0.06f;
        return fire.y + 2.0 + rise;
    }

    private record Shot(Vec3d eye, Vec3d look) {}

    private static Shot shot(float s) {
        // 1. the front: push in on the fire and the growing chains
        float a = MathHelper.clamp(s / 5.5f, 0f, 1f);
        Shot front = new Shot(fire.add(0, 3.2 - 0.6 * a, -8.5 + 2.0 * a), fire.add(0, 1.4, 0));
        // 2. the side: It rises, the camera rises and looks up with it
        float b = MathHelper.clamp((s - 5.5f) / 6.5f, 0f, 1f);
        double y = itY(s);
        Shot side = new Shot(fire.add(7.5 - 1.0 * b, 2.0 + 3.5 * b, 1.5), new Vec3d(fire.x, y, fire.z));
        // 3. from high above: the pentagram
        float c = MathHelper.clamp((s - 12f) / 4.5f, 0f, 1f);
        Shot above = new Shot(fire.add(0.01, 9.5 + 1.5 * c, -3.0 + 1.0 * c), fire.add(0, -0.5, 0));
        if (s < 5.0f) return front;
        if (s < 6.0f) return blend(front, side, smooth((s - 5.0f) / 1.0f));
        if (s < 11.8f) return side;
        if (s < 12.8f) return blend(side, above, smooth((s - 11.8f) / 1.0f));
        return above;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static Shot blend(Shot p, Shot q, float t) {
        return new Shot(p.eye().lerp(q.eye(), t), p.look().lerp(q.look(), t));
    }

    /** The camera this frame: position, yaw, pitch. */
    public static double[] pose() {
        Shot sh = shot(time());
        Vec3d d = sh.look().subtract(sh.eye());
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        float pitch = (float) -(MathHelper.atan2(d.y, horiz) * MathHelper.DEGREES_PER_RADIAN);
        return new double[]{sh.eye().x, sh.eye().y, sh.eye().z, yaw, pitch};
    }
}
