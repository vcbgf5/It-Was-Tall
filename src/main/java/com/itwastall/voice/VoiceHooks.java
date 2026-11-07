package com.itwastall.voice;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Predicate;

/**
 * The bridge to the optional Simple Voice Chat plugin ({@link ItWasTallVoicechat}): the rest of the mod never
 * touches the voice chat API, it only asks these hooks (all null / false without the mod).
 */
public final class VoiceHooks {
    /** Set when Simple Voice Chat loaded our plugin on this server. */
    public static volatile boolean available;
    /** It plays back a stolen voice snippet near that player now (god panel / command). */
    public static volatile Predicate<ServerPlayerEntity> mimicNow;

    private VoiceHooks() {}
}
