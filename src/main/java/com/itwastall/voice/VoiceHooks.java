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
    /** DEBUG: It plays the NEWEST voice piece it has (anyone's) near that player now; false = nothing recorded. */
    public static volatile Predicate<ServerPlayerEntity> mimicLatestNow;
    /**
     * DEBUG: records the next 5 s of {@code from}'s voice, then It plays exactly that back near {@code target} at
     * once; {@code report} gets what happened (from the server thread). False = voice chat is not running.
     */
    public static volatile RecordThenMimic recordThenMimic;

    @FunctionalInterface
    public interface RecordThenMimic {
        boolean start(ServerPlayerEntity from, ServerPlayerEntity target, java.util.function.Consumer<String> report);
    }

    private VoiceHooks() {}
}
