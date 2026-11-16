package io.github.brainage04.simplehomes.neoforge;

import io.github.brainage04.simplehomes.HomeCommandRegistration;
import io.github.brainage04.simplehomes.SimpleHomes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(SimpleHomes.MOD_ID)
public final class SimpleHomesNeoForge {
    public SimpleHomesNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(SimpleHomesNeoForge::initialize);
        NeoForge.EVENT_BUS.addListener(SimpleHomesNeoForge::registerCommands);
        NeoForge.EVENT_BUS.addListener(SimpleHomesNeoForge::playerLoggedIn);
    }

    private static void initialize(FMLCommonSetupEvent event) {
        SimpleHomes.initialize(FMLPaths.CONFIGDIR.get());
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        HomeCommandRegistration.register(event.getDispatcher());
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SimpleHomes.playerJoined(player);
    }
}
