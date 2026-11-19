package io.github.brainage04.simplehomes;

import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;

/** The film around SimpleHomes' client GameTest: the open chat, the captions and their shots. */
@SuppressWarnings("UnstableApiUsage")
public final class SimpleHomesShowcase {
    private static final int SHOT_TICKS = 50;

    private SimpleHomesShowcase() {}

    /** Keeps the chat open, so the film shows each command's reply. */
    public static void openChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreenAndShow(new ChatScreen("", false)));
    }

    public static void closeChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreenAndShow(null));
    }

    public static void homeCreated(ClientGameTestContext context) {
        ClientGameTestRecorder.showStepAndHold(
                context,
                "simplehomes.create",
                "Named home created",
                "/sethome base persists the current dimension, position, yaw, and pitch within the"
                        + " configured limit",
                SHOT_TICKS);
    }

    public static void homeRestored(ClientGameTestContext context) {
        ClientGameTestRecorder.showStepAndHold(
                context,
                "simplehomes.teleport",
                "Named home restored",
                "/home base returns to the persisted home and confirms the teleport in chat",
                SHOT_TICKS);
    }

    /** Shares the home with its owner, which changes nothing, and shows the reply. */
    public static void shareWithYourself(ClientGameTestContext context) {
        String playerName = context.computeOnClient(client -> client.player.getName().getString());
        ClientGameTestCommands.runCommand(context, "sharehome base " + playerName);
        context.waitTicks(20);
        ClientGameTestRecorder.showStepAndHold(
                context,
                "simplehomes.sharing",
                "Owner access unchanged",
                "Sharing a home with yourself leaves your existing owner access unchanged",
                SHOT_TICKS);
    }
}
