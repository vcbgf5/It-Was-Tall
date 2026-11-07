package com.itwastall.voice;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.entity.Walkie;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat plugin of It Was Tall (loaded only when that mod is installed):
 * <ul>
 *   <li>THE WALKIE-TALKIE: while a player holds the talk button, their voice also goes to every other player
 *   (who is out of normal voice range) — band-limited, clipped and crackling like a radio;</li>
 *   <li>IT MIMICS YOUR VOICE (config itMimicsVoices, off by default): short pieces of what players say are kept in
 *   memory only, and It plays them back later, slowed and wrong, from the dark (see {@link VoiceMimic}).</li>
 * </ul>
 */
public class ItWasTallVoicechat implements VoicechatPlugin {
    public static final String CAT_WALKIE = "itwastall_walkie";
    public static final String CAT_IT = "itwastall_it";

    static volatile VoicechatServerApi api;
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final Map<UUID, OpusEncoder> encoders = new ConcurrentHashMap<>();
    private final Map<UUID, RadioFx> effects = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return ItWasTall.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        VoiceHooks.available = true;
        VoiceMimic.init();
        ItWasTall.LOGGER.info("Simple Voice Chat found: walkie-talkie enabled.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CAT_WALKIE).setName("Walkie-talkie")
                    .setDescription("Voices coming through the It Was Tall walkie-talkie").build());
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CAT_IT).setName("It")
                    .setDescription("Voices It plays back from the dark").build());
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatServerApi a = api;
        VoicechatConnection sender = event.getSenderConnection();
        if (a == null || sender == null) return;
        UUID id = sender.getPlayer().getUuid();
        boolean talking = Walkie.isTransmitting(id);
        boolean record = ItWasTallConfig.INSTANCE.itMimicsVoices;
        if (!talking && !record) return;
        MicrophonePacket packet = event.getPacket();
        byte[] data = packet.getOpusEncodedData();
        if (data == null || data.length == 0) {          // the end of a sentence
            VoiceMimic.endOfSpeech(id);
            OpusDecoder d = decoders.get(id);
            if (d != null) d.resetState();
            return;
        }
        short[] pcm;
        try {
            pcm = decoders.computeIfAbsent(id, u -> a.createDecoder()).decode(data);
        } catch (RuntimeException ex) {
            return;
        }
        if (record) VoiceMimic.record(id, pcm);
        if (!talking || !(sender.getPlayer().getPlayer() instanceof ServerPlayerEntity from) || from.getServer() == null) return;

        short[] radio = effects.computeIfAbsent(id, u -> new RadioFx()).apply(pcm);
        byte[] encoded = encoders.computeIfAbsent(id, u -> a.createEncoder()).encode(radio);
        double near = a.getVoiceChatDistance();
        List<ServerPlayerEntity> players;
        try {
            players = List.copyOf(from.getServer().getPlayerManager().getPlayerList());
        } catch (RuntimeException ex) {
            return;
        }
        for (ServerPlayerEntity to : players) {
            if (to == from) continue;
            if (to.getWorld() == from.getWorld() && to.squaredDistanceTo(from) < near * near) continue;   // they hear the real voice
            VoicechatConnection c = a.getConnectionOf(to.getUuid());
            if (c == null || !c.isConnected() || c.isDisabled()) continue;
            a.sendStaticSoundPacketTo(c, packet.staticSoundPacketBuilder().opusEncodedData(encoded).category(CAT_WALKIE).build());
        }
    }

    /** A voice through a cheap radio: band-passed (~300-3000 Hz), driven into clipping, hiss and crackle. */
    static final class RadioFx {
        private float hpPrevIn, hpPrevOut, lp;
        private final java.util.Random random = new java.util.Random();

        short[] apply(short[] in) {
            short[] out = new short[in.length];
            float hpA = 0.96f, lpA = 0.32f;
            boolean dropout = random.nextInt(40) == 0;
            for (int i = 0; i < in.length; i++) {
                float x = in[i] / 32768f;
                float hp = hpA * (hpPrevOut + x - hpPrevIn);      // one-pole high-pass
                hpPrevIn = x;
                hpPrevOut = hp;
                lp += lpA * (hp - lp);                             // one-pole low-pass
                float y = (float) Math.tanh(lp * 2.6f) * 0.8f;     // drive
                y += (random.nextFloat() - 0.5f) * 0.035f;         // hiss
                if (random.nextInt(4000) == 0) y += (random.nextFloat() - 0.5f) * 0.6f;   // crackle
                if (dropout) y *= 0.15f;
                out[i] = (short) Math.max(-32767, Math.min(32767, y * 32767f));
            }
            return out;
        }
    }
}
