package io.github.brainage04.modernminecraftmodtemplate.command.core;

import io.github.brainage04.modernminecraftmodtemplate.command.ExampleCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ExampleCommand.initialize(dispatcher);
        });
    }
}
