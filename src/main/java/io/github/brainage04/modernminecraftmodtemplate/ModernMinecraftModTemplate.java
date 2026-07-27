package io.github.brainage04.modernminecraftmodtemplate;

import io.github.brainage04.modernminecraftmodtemplate.command.core.ModCommands;
import io.github.brainage04.modernminecraftmodtemplate.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModernMinecraftModTemplate implements ModInitializer {
    public static final String MOD_ID = "modernminecraftmodtemplate";
    public static final String MOD_NAME = "ModernMinecraftModTemplate";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	@Override
	public void onInitialize() {
        LOGGER.info("{} initialising...", MOD_NAME);

        ModConfig.init();
        ModCommands.initialize();

        if (ModConfig.CONFIG.logConfigOnStartup.get()) {
            LOGGER.info(
                    "Loaded config: message='{}', mode={}, featuredItem={}, retries={}",
                    ModConfig.CONFIG.welcomeMessage.get(),
                    ModConfig.CONFIG.syncMode.get(),
                    ModConfig.CONFIG.featuredItem.get(),
                    ModConfig.CONFIG.startupRetries.get()
            );
        }

        LOGGER.info("{} initialised.", MOD_NAME);
	}
}
