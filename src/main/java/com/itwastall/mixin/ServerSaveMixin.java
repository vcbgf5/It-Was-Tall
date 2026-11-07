package com.itwastall.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every world save (autosave, /save-all, the pause menu in singleplayer) shows the quill to everyone online. */
@Mixin(MinecraftServer.class)
public abstract class ServerSaveMixin {
    @Inject(method = "saveAll(ZZZ)Z", at = @At("TAIL"))
    private void itwastall$saved(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        com.itwastall.world.Checkpoint.iconAll((MinecraftServer) (Object) this);
    }
}
