package com.itwastall.registry;

import com.itwastall.ItWasTall;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The voice of It and the radio (tools/gen_voices.py: espeak-ng text to speech, bent out of shape).
 * Sound ids: voice.it.&lt;key&gt; and voice.radio.&lt;key&gt;.
 */
public final class ModVoices {
    public static final String[] IT_ANYWHERE = {"im_here", "know_everything", "see_you", "dont_turn", "found_you",
            "cant_hide", "come_closer", "why_running", "always_here", "look_at_me", "dark_now", "go_to_sleep",
            "behind_you", "nobody_coming"};
    public static final String[] IT_CAVE = {"where_are_you", "what_mining", "dig_deeper", "hear_digging", "quiet_down_here"};
    public static final String[] IT_BASE = {"let_me_in", "where_you_sleep", "door_open"};
    public static final String[] IT_CHASE = {"run", "there_you_are", "found_you"};
    public static final String[] RADIO_LINES = {"not_a_test", "do_not_go_out", "it_is_coming", "count_rooms", "keep_lights",
            "at_your_door", "not_the_window", "lost_contact", "dont_answer", "knows_name"};
    private static final String[] RADIO_OTHER = {"red_moon", "position", "repeat", "x", "z", "minus",
            "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "static", "squelch"};

    private static final Map<String, SoundEvent> IT = new HashMap<>();
    private static final Map<String, SoundEvent> RADIO = new HashMap<>();

    private ModVoices() {}

    public static void init() {
        for (String[] group : new String[][]{IT_ANYWHERE, IT_CAVE, IT_BASE, IT_CHASE}) {
            for (String key : group) IT.computeIfAbsent(key, k -> register("voice.it." + k));
        }
        for (String key : RADIO_LINES) RADIO.computeIfAbsent(key, k -> register("voice.radio." + k));
        for (String key : RADIO_OTHER) RADIO.computeIfAbsent(key, k -> register("voice.radio." + k));
    }

    public static SoundEvent it(String key) {
        return IT.get(key);
    }

    public static SoundEvent radio(String key) {
        return RADIO.get(key);
    }

    private static SoundEvent register(String name) {
        Identifier id = ItWasTall.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
