package com.itwastall;

import com.itwastall.command.ItWasTallCommand;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.entity.AmbientSounds;
import com.itwastall.entity.FalsePresences;
import com.itwastall.entity.HauntDirector;
import com.itwastall.entity.MineEvents;
import com.itwastall.entity.ScareSounds;
import com.itwastall.entity.WindowKnocks;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModItems;
import com.itwastall.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItWasTall implements ModInitializer {
    public static final String MOD_ID = "itwastall";
    public static final Logger LOGGER = LoggerFactory.getLogger("It Was Tall");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ItWasTallConfig.load();
        ModSounds.init();
        ModEntities.init();
        ModItems.init();
        ModNetworking.init();
        MineEvents.init();
        AmbientSounds.init();
        ScareSounds.init();
        HauntDirector.init();
        FalsePresences.init();
        WindowKnocks.init();
        // multiplayer: forget per-player timers when someone leaves
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = handler.getPlayer().getUuid();
            AmbientSounds.forget(uuid);
            ScareSounds.forget(uuid);
            MineEvents.forget(uuid);
            HauntDirector.forget(uuid);
            FalsePresences.forget(uuid);
            WindowKnocks.forget(uuid);
        });
        CommandRegistrationCallback.EVENT.register(ItWasTallCommand::register);
        // Director: load phase/stage/tension from the world data before anything runs...
        ServerLifecycleEvents.SERVER_STARTED.register(Director::init);
        // ...tick the activation counter / tension / stage promotions (ORDER NOTE: registered
        // AFTER the mechanic systems above on purpose so they see the fresh tick's state first;
        // order is not critical, Director only reads + writes the shared live state...)
        ServerTickEvents.END_SERVER_TICK.register(Director::tick);
        // ...and flush the state when the server stops.
        ServerLifecycleEvents.SERVER_STOPPING.register(Director::stop);
        LOGGER.info("It was tall.");
    }
}
