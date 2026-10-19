package io.github.brainage04.simplehomes;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;

/**
 * Loader-neutral server GameTest bodies. Both loaders compile this source set into their GameTest
 * mods: Fabric runs them through {@code @GameTest} methods, NeoForge through registered test
 * functions and {@code test_instance} data.
 */
public final class SimpleHomesGameTests {
	private SimpleHomesGameTests() {
	}

	public static void allHomeCommandsAreRegistered(GameTestHelper helper) {
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		for (String command : List.of("sethome", "home", "homeof", "sharehome")) {
			if (root.getChild(command) == null) throw new AssertionError("Expected /" + command + " to be registered");
		}
		helper.succeed();
	}

	public static void namedHomesRespectLimitsAndTeleport(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		HomeData data = HomeData.get(helper.getLevel().getServer());
		BlockPos homePosition = helper.absolutePos(new BlockPos(2, 2, 2));
		HomeLocation home = new HomeLocation(helper.getLevel().dimension().identifier(), homePosition, 45.0F, 0.0F);
		helper.assertTrue(data.setHome(player.getUUID(), "base", home, 1) == HomeData.SetResult.CREATED,
				"The first named home must be created");
		helper.assertTrue(data.setHome(player.getUUID(), "mine", home, 1) == HomeData.SetResult.LIMIT_REACHED,
				"A new home beyond the configured limit must be rejected");
		player.teleportTo(20.0D, homePosition.getY(), 20.0D);
		helper.assertTrue(HomeTeleportService.teleport(player, home, "test home") == 1,
				"A saved home must teleport successfully");
		helper.assertTrue(player.blockPosition().distSqr(homePosition) <= 1.0D,
				"A saved home must restore its dimension and position");
		helper.succeed();
	}

	public static void homeSharingIsOwnerAndHomeScoped(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer guest = helper.makeMockServerPlayerInLevel();
		HomeData data = HomeData.get(helper.getLevel().getServer());
		BlockPos homePosition = helper.absolutePos(new BlockPos(2, 2, 2));
		HomeLocation home = new HomeLocation(helper.getLevel().dimension().identifier(), homePosition, 0.0F, 0.0F);
		data.setHome(owner.getUUID(), "base", home, 10);
		data.setHome(owner.getUUID(), "mine", home, 10);
		helper.assertTrue(data.grant(owner.getUUID(), "base", List.of(guest.getUUID())) == 1,
				"Sharing a home must grant one owner-scoped entry");
		helper.assertTrue(data.allows(owner.getUUID(), "base", guest.getUUID()),
				"The shared home must be accessible to the guest");
		helper.assertTrue(!data.allows(owner.getUUID(), "mine", guest.getUUID()),
				"Sharing one home must not expose the owner's other homes");
		helper.assertTrue(HomeTeleportService.teleport(guest, home, "shared test home") == 1,
				"An allowed guest must be able to use the shared location");
		helper.assertTrue(guest.blockPosition().distSqr(homePosition) <= 1.0D,
				"The guest must reach the shared home");
		helper.succeed();
	}

	public static void commandsNormalizeNamesAndEnforceLimits(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		HomeData data = HomeData.get(helper.getLevel().getServer());
		BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
		player.teleportTo(target.getX() + 0.25D, target.getY(), target.getZ() + 0.75D);
		player.setYRot(70.0F);
		player.setXRot(-15.0F);
		helper.assertTrue(execute(helper, player, "sethome BaSe_1") == 1, "A valid mixed-case home must be created");
		HomeLocation home = data.getHome(player.getUUID(), "base_1").orElseThrow();
		helper.assertTrue(home.position().equals(target) && home.yaw() == 70.0F && home.pitch() == -15.0F,
				"The command must save block position and rotation");
		helper.assertTrue(execute(helper, player, "sethome bad.name") == 0, "Invalid characters must be rejected");
		helper.assertTrue(execute(helper, player, "sethome " + "a".repeat(33)) == 0, "Names beyond 32 characters must be rejected");
		for (int i = 1; i < SimpleHomesConfig.maximumHomes(); i++) {
			execute(helper, player, "sethome home_" + i);
		}
		helper.assertTrue(execute(helper, player, "sethome overflow") == 0, "The command must enforce the configured limit");
		helper.assertTrue(execute(helper, player, "sethome BASE_1") == 1, "Updating at the limit must remain possible");
		player.teleportTo(target.getX() + 15.0D, target.getY(), target.getZ() + 15.0D);
		helper.assertTrue(execute(helper, player, "home BASE_1") == 1, "Home lookup must be case-insensitive");
		helper.assertTrue(player.position().distanceToSqr(home.position().getCenter().add(0.0D, -0.5D, 0.0D)) < 0.0001D,
				"The home command must restore the saved block's horizontal center");
		helper.assertTrue(execute(helper, player, "home missing") == 0, "A missing home must fail");
		helper.succeed();
	}

	public static void sharingCommandsEnforceAndRevokeAccess(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer guest = helper.makeMockServerPlayerInLevel();
		var players = helper.getLevel().getServer().getPlayerList();
		String tag = "simplehomes_" + owner.getUUID();
		owner.addTag(tag);
		// Entity arguments resolve online players through the live player list.
		players.getPlayers().addAll(List.of(owner, guest));
		players.getPlayersByUUID().put(owner.getUUID(), owner);
		players.getPlayersByUUID().put(guest.getUUID(), guest);
		try {
			execute(helper, owner, "sethome base");
			execute(helper, owner, "sethome mine");
			String sharedHome = "homeof @a[tag=" + tag + "] base";
			helper.assertTrue(execute(helper, guest, sharedHome) == 0, "An uninvited guest must be denied");
			helper.assertTrue(execute(helper, owner, "sharehome base " + guest.getUUID()) == 1, "Sharing must grant access");
			helper.assertTrue(execute(helper, owner, "sharehome base " + guest.getUUID()) == 0, "Duplicate sharing must not count twice");
			helper.assertTrue(execute(helper, guest, sharedHome) == 1, "A shared home must be usable");
			helper.assertTrue(execute(helper, guest, "homeof @a[tag=" + tag + "] mine") == 0, "Other homes must stay private");
			helper.assertTrue(execute(helper, guest, "sharehome base " + owner.getUUID()) == 0, "Guests cannot share another owner's home");
			helper.assertTrue(execute(helper, owner, "sharehome revoke base " + guest.getUUID()) == 1, "Revocation must remove access");
			helper.assertTrue(execute(helper, guest, sharedHome) == 0, "Revoked guests must be denied");
			helper.assertTrue(execute(helper, owner, "homeof @s base") == 1, "Owners must retain access");
			helper.assertTrue(execute(helper, owner, "sethome revoke") == 1, "'revoke' must remain a valid home name");
			helper.assertTrue(execute(helper, owner, "sharehome revoke " + guest.getUUID()) == 1, "A home named 'revoke' must be shareable");
			helper.assertTrue(execute(helper, guest, "homeof @a[tag=" + tag + "] revoke") == 1, "The guest must reach the home named 'revoke'");
			helper.assertTrue(execute(helper, owner, "sharehome revoke revoke " + guest.getUUID()) == 1, "Access to a home named 'revoke' must be revocable");
			helper.assertTrue(execute(helper, guest, "homeof @a[tag=" + tag + "] revoke") == 0, "Revocation must apply to the home named 'revoke'");
			helper.succeed();
		} finally {
			players.getPlayers().removeAll(List.of(owner, guest));
			players.getPlayersByUUID().remove(owner.getUUID());
			players.getPlayersByUUID().remove(guest.getUUID());
		}
	}

	public static void teleportsAcrossDimensionsAndRejectsMissingDimensions(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var server = helper.getLevel().getServer();
		var nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "The test server must provide the Nether");
		try {
			HomeLocation home = new HomeLocation(Level.NETHER.identifier(), new BlockPos(8, 80, -12), 65.0F, -25.0F);
			helper.assertTrue(HomeTeleportService.teleport(player, home, "Nether home") == 1, "Cross-dimension teleport must succeed");
			helper.assertTrue(player.level() == nether, "Teleport must restore the saved dimension");
			helper.assertTrue(player.getX() == 8.5D && player.getY() == 80.0D && player.getZ() == -11.5D,
					"Teleport must restore block position");
			helper.assertTrue(player.getYRot() == 65.0F && player.getXRot() == -25.0F, "Teleport must restore rotation");
			var before = player.position();
			HomeLocation missing = new HomeLocation(Identifier.fromNamespaceAndPath("simplehomes", "missing"), BlockPos.ZERO, 0.0F, 0.0F);
			helper.assertTrue(HomeTeleportService.teleport(player, missing, "missing home") == 0, "Unavailable dimensions must fail");
			helper.assertTrue(player.level() == nether && player.position().equals(before), "A failed teleport must not move the player");
			helper.succeed();
		} finally {
			player.teleportTo(helper.getLevel(), 0.5D, 80.0D, 0.5D, Set.of(), 0.0F, 0.0F, false);
		}
	}

	public static void rejectedTeleportDoesNotReportSuccess(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var before = player.position();
		player.discard();
		HomeLocation home = new HomeLocation(helper.getLevel().dimension().identifier(), helper.absolutePos(new BlockPos(2, 2, 2)), 0.0F, 0.0F);
		helper.assertTrue(HomeTeleportService.teleport(player, home, "test home") == 0,
				"A teleport rejected by Minecraft must not return success");
		helper.assertTrue(player.position().equals(before), "A rejected teleport must not move the player");
		helper.succeed();
	}

	private static int execute(GameTestHelper helper, ServerPlayer player, String command) {
		var server = helper.getLevel().getServer();
		try {
			return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withEntity(player));
		} catch (CommandSyntaxException exception) {
			throw new AssertionError("Could not execute /" + command, exception);
		}
	}
}
