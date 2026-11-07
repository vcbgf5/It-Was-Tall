package com.itwastall.client;

/** Client side of THE BLOOD MOON: the sky, the fog and the moon turn red (BloodMoonMixins). */
public final class BloodMoonClient {
    public static boolean active;

    private BloodMoonClient() {}

    /** Pulls a colour channel set toward dark blood red, keeping how bright it was. */
    public static float[] tint(float r, float g, float b) {
        float lum = Math.max(0.05f, (r + g + b) / 3f);
        return new float[]{Math.min(1f, lum * 1.9f + 0.08f), lum * 0.32f, lum * 0.28f};
    }
}
