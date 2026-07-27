package io.github.brainage04.simplehomes.fabric;

import io.github.brainage04.simplehomes.HomeCommandRegistration;
import io.github.brainage04.simplehomes.SimpleHomes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public final class SimpleHomesFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		SimpleHomes.initialize(FabricLoader.getInstance().getConfigDir());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				HomeCommandRegistration.register(dispatcher));
	}
}
