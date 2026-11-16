package io.github.brainage04.simplehomes;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * Records the name a player joined under, so "last seen" in the share lists is their real last
     * login rather than the last time an owner shared or revoked under that name. Only a guest is
     * recorded: the lists can only show guests, and recording every player who ever joined would
     * grow the saved history past the guest set it is bounded by.
     */
    public static void playerJoined(ServerPlayer player) {
        HomeData data = HomeData.get(player.level().getServer());
        if (!data.isGuest(player.getUUID())) return;
        data.sawName(player.getUUID(), player.getScoreboardName(), System.currentTimeMillis());
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
