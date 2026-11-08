package com.itwastall.client.mixin;

import com.itwastall.client.sound.CorruptedMusic;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's own music, sometimes played corrupted ({@link CorruptedMusic}); nothing else plays over it. */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void itwastall$waitForCorrupted(CallbackInfo ci) {
        if (CorruptedMusic.isPlaying()) ci.cancel();
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void itwastall$corrupt(MusicSound music, CallbackInfo ci) {
        if (CorruptedMusic.tryReplace(music)) ci.cancel();
    }
}
