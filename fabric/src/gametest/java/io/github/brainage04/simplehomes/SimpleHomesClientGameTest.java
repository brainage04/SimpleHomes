package io.github.brainage04.simplehomes;

import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Properties;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleHomesClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties serverProperties = ClientGameTestServers.flatServerProperties();
		ClientGameTestServers.withDedicatedServer(context, serverProperties, "SimpleHomes visual GameTest", server -> { try {
				ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
				assertCommandTree(context);
				context.runOnClient(client -> client.setScreenAndShow(new ChatScreen("", false)));
				ClientGameTestRecorder.startRecording(context);

				runCommand(context, "sethome base");
				context.waitTicks(20);
				server.runOnServer(minecraftServer -> {
					ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
					if (HomeData.get(minecraftServer).getHome(player.getUUID(), "base").isEmpty()) {
						throw new AssertionError("The client command must create a persisted home.");
					}
				});
				ClientGameTestRecorder.showStep(
						context,
						"simplehomes.create",
						"Named home created",
						"/sethome base persists the current dimension, position, yaw, and pitch within the configured limit"
				);
				context.waitTicks(50);

				server.runOnServer(minecraftServer -> {
					ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
					player.teleportTo(
							player.level(),
							player.getX() + 12.0D,
							player.getY(),
							player.getZ() + 12.0D,
							Set.of(),
							player.getYRot(),
							player.getXRot(),
							false
					);
				});
				context.waitTicks(20);
				runCommand(context, "home base");
				context.waitTicks(20);
				server.runOnServer(minecraftServer -> {
					ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
					HomeLocation home = HomeData.get(minecraftServer).getHome(player.getUUID(), "base").orElseThrow();
					if (player.level().dimension().identifier().equals(home.dimension())
							&& player.blockPosition().equals(home.position())) return;
					throw new AssertionError("The client /home command must restore the saved dimension and block position.");
				});
				ClientGameTestRecorder.showStep(
						context,
						"simplehomes.teleport",
						"Named home restored",
						"/home base returns to the persisted home and confirms the teleport in chat"
				);
				context.waitTicks(50);

				String playerName = context.computeOnClient(client -> client.player.getName().getString());
				runCommand(context, "sharehome base " + playerName);
				context.waitTicks(20);
				ClientGameTestRecorder.showStep(
						context,
						"simplehomes.sharing",
						"Owner access unchanged",
						"Sharing a home with yourself leaves your existing owner access unchanged"
				);
				context.waitTicks(50);
			} finally {
				context.runOnClient(client -> client.setScreenAndShow(null));
			} });
	}

	private static void assertCommandTree(ClientGameTestContext context) {
		context.computeOnClient(client -> {
			if (client.getConnection() == null) throw new AssertionError("Expected a connected client.");
			var root = client.getConnection().getCommands().getRoot();
			for (String command : List.of("sethome", "home", "homeof", "sharehome")) {
				if (root.getChild(command) == null) throw new AssertionError("Expected /" + command + " in the client command tree.");
			}
			return null;
		});
	}

	private static void runCommand(ClientGameTestContext context, String command) {
		context.runOnClient(client -> {
			if (client.getConnection() == null) throw new AssertionError("Expected a connected client to run /" + command + '.');
			client.getConnection().sendCommand(command);
		});
	}
}
