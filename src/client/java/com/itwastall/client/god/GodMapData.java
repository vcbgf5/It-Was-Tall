package com.itwastall.client.god;

import com.itwastall.net.GodMapPayload;
import net.minecraft.block.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The last mini-map the server sent to the god's panel, and every player's status by name. */
public final class GodMapData {
    @Nullable
    static GodMapPayload map;
    static final Map<String, String> STATUS = new HashMap<>();
    private static final int[] ARGB = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            if ((i >> 2) == 0) {
                ARGB[i] = 0xFF050505;
                continue;
            }
            int abgr = MapColor.getRenderColor(i);
            ARGB[i] = 0xFF000000 | ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
        }
    }

    private GodMapData() {}

    public static void update(GodMapPayload payload) {
        map = payload;
        for (GodMapPayload.Marker m : payload.markers()) if (m.kind() != 3) STATUS.put(m.name(), m.status());
    }

    public static void clear() {
        map = null;
        STATUS.clear();
    }

    static int color(byte b) {
        return ARGB[b & 0xFF];
    }
}
