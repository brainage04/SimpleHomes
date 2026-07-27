package io.github.brainage04.simplehomes.neoforge;

import io.github.brainage04.simplehomes.HomeData;
import io.github.brainage04.simplehomes.HomeLocation;
import io.github.brainage04.simplehomes.HomeTeleportService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class SimpleHomesNeoForgeGameTests {
	public void allHomeCommandsAreRegistered(GameTestHelper helper) {
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		for (String command : List.of("sethome", "home", "homeof", "sharehome")) {
			if (root.getChild(command) == null) throw new AssertionError("Expected /" + command + " to be registered");
		}
		helper.succeed();
	}

	public void namedHomesRespectLimitsAndTeleport(GameTestHelper helper) {
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

	public void homeSharingIsOwnerAndHomeScoped(GameTestHelper helper) {
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
}
