package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: the player gazed at a client-side fake figure (FalsePresence renders).
 * Two meanings — selected by {@code behind}:
 * <ul>
 *   <li>{@code false}: a deliberate look at a WINDOW figure (§12) — It noticed, task follows.</li>
 *   <li>{@code true}: the player TURNED AROUND at the behind-figure (§15.4 NIE PATRZ) —
 *       that is exactly what It told them not to do, so the task fails.</li>
 * </ul>
 */
public record WindowSeenPayload(boolean behind) implements CustomPayload {
    public static final CustomPayload.Id<WindowSeenPayload> ID =
            new CustomPayload.Id<>(ItWasTall.id("window_seen"));
    public static final PacketCodec<RegistryByteBuf, WindowSeenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, WindowSeenPayload::behind,
            WindowSeenPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
