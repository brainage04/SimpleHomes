package io.github.brainage04.simplehomes;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleHomes {
    public static final String MOD_ID = "simplehomes";
    public static final String MOD_NAME = "SimpleHomes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final ModFeedback FEEDBACK = ModFeedback.create(MOD_NAME);

    private SimpleHomes() {}

    public static void initialize(Path configDirectory) {
        SimpleHomesConfig.load(configDirectory);
        ServerModHelpRegistry.register(
                ServerModHelpEntry.playerOnly(
                        MOD_ID,
                        MOD_NAME,
                        "Adds named, persistent homes that you can share with other players.",
                        "/simplehomes help"));
        LOGGER.info(
                "{} initialized with a maximum of {} homes per player.",
                MOD_NAME,
                SimpleHomesConfig.maximumHomes());
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
