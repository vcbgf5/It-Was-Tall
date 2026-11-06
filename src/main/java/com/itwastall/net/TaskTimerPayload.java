package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: the TASK TIMER in the corner of the screen. Sent every second while a task runs, at once
 * when one starts / changes phase / ends. An empty title means "no task" (the timer slides away).
 *
 * @param left   seconds left in the current phase
 * @param total  length of the current phase in seconds (for the shrinking bar)
 * @param danger true when running out of time loses the task (douse the lights, the offering)
 * @param target a block the client outlines through walls (the offering's chest)
 */
public record TaskTimerPayload(String title, String detail, int left, int total, boolean danger,
                               java.util.Optional<net.minecraft.util.math.BlockPos> target) implements CustomPayload {
    public static final CustomPayload.Id<TaskTimerPayload> ID = new CustomPayload.Id<>(ItWasTall.id("task_timer"));
    public static final PacketCodec<RegistryByteBuf, TaskTimerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(48), TaskTimerPayload::title,
            PacketCodecs.string(96), TaskTimerPayload::detail,
            PacketCodecs.VAR_INT, TaskTimerPayload::left,
            PacketCodecs.VAR_INT, TaskTimerPayload::total,
            PacketCodecs.BOOL, TaskTimerPayload::danger,
            PacketCodecs.optional(net.minecraft.util.math.BlockPos.PACKET_CODEC), TaskTimerPayload::target,
            TaskTimerPayload::new);

    public static final TaskTimerPayload NONE = new TaskTimerPayload("", "", 0, 0, false, java.util.Optional.empty());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
