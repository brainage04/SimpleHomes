package io.github.brainage04.simplehomes;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleHomes {
    public static final String MOD_ID = "simplehomes";
    public static final String MOD_NAME = "SimpleHomes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final ModFeedback FEEDBACK = ModFeedback.create(MOD_NAME);
    public static GameRule<Integer> MAXIMUM_HOMES;
    public static GameRule<Boolean> RESET_FALL;

    private SimpleHomes() {}

    public static void initialize(GameRule<Integer> maximumHomes, GameRule<Boolean> resetFall) {
        MAXIMUM_HOMES = maximumHomes;
        RESET_FALL = resetFall;
        ServerModHelpRegistry.register(
                new ServerModHelpEntry(
                        MOD_ID,
                        MOD_NAME,
                        "Adds named, persistent homes that you can share with other players.",
                        "/simplehomes help",
                        "/gamerule simplehomes:maximum_homes, /gamerule simplehomes:reset_fall"));
        LOGGER.info("{} initialized.", MOD_NAME);
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
