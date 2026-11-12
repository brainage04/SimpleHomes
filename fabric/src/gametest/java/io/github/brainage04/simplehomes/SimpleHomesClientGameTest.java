package io.github.brainage04.simplehomes;

import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleHomesClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ClientGameTestServers.withDedicatedServer(
                context,
                ClientGameTestServers.flatServerProperties(),
                "SimpleHomes visual GameTest",
                server -> {
                    try {
                        ClientGameTestCommands.assertCommandTree(
                                context, "sethome", "home", "homeof", "sharehome");
                        SimpleHomesShowcase.openChat(context);

                        ClientGameTestCommands.runCommand(context, "sethome base");
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player = player(minecraftServer);
                                    if (HomeData.get(minecraftServer)
                                            .getHome(player.getUUID(), "base")
                                            .isEmpty()) {
                                        throw new AssertionError(
                                                "The client command must create a persisted home.");
                                    }
                                });
                        SimpleHomesShowcase.homeCreated(context);

                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player = player(minecraftServer);
                                    player.teleportTo(
                                            player.level(),
                                            player.getX() + 12.0D,
                                            player.getY(),
                                            player.getZ() + 12.0D,
                                            Set.of(),
                                            player.getYRot(),
                                            player.getXRot(),
                                            false);
                                });
                        context.waitTicks(20);
                        ClientGameTestCommands.runCommand(context, "home base");
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player = player(minecraftServer);
                                    HomeLocation home =
                                            HomeData.get(minecraftServer)
                                                    .getHome(player.getUUID(), "base")
                                                    .orElseThrow();
                                    if (player.level()
                                                    .dimension()
                                                    .identifier()
                                                    .equals(home.dimension())
                                            && player.blockPosition()
                                                    .equals(
                                                            BlockPos.containing(
                                                                    home.position()
                                                                            .left()
                                                                            .orElseThrow())))
                                        return;
                                    throw new AssertionError(
                                            "The client /home command must restore the saved"
                                                    + " dimension and block position.");
                                });
                        SimpleHomesShowcase.homeRestored(context);

                        SimpleHomesShowcase.shareWithYourself(context);
                    } finally {
                        SimpleHomesShowcase.closeChat(context);
                    }
                });
    }

    private static ServerPlayer player(MinecraftServer minecraftServer) {
        return minecraftServer.getPlayerList().getPlayers().getFirst();
    }
}
