package io.github.brainage04.modernminecraftmodtemplate;

import io.github.brainage04.modernminecraftmodtemplate.command.core.ClientModCommands;
import net.fabricmc.api.ClientModInitializer;

public class ModernMinecraftModTemplateClient implements ClientModInitializer {
    private static volatile boolean initialized;

    @Override
    public void onInitializeClient() {
        ClientModCommands.initialize();
        initialized = true;

        ModernMinecraftModTemplate.LOGGER.info("{} client initialised.", ModernMinecraftModTemplate.MOD_NAME);
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
