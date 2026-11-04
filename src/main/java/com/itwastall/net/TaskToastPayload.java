package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: TaskToast (MODPLAN §16) — new task / completed / failed, drawn by the
 * client's TaskToast HUD box (title + message + kind-coloured accent). Server-composed text.
 */
public record TaskToastPayload(int kind, String title, String text) implements CustomPayload {
    public static final int KIND_NEW = 0;
    public static final int KIND_COMPLETED = 1;
    public static final int KIND_FAILED = 2;

    public static final CustomPayload.Id<TaskToastPayload> ID = new CustomPayload.Id<>(ItWasTall.id("task_toast"));
    public static final PacketCodec<RegistryByteBuf, TaskToastPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TaskToastPayload::kind,
            PacketCodecs.string(64), TaskToastPayload::title,
            PacketCodecs.string(256), TaskToastPayload::text,
            TaskToastPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
