package io.github.brainage04.simplehomes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SimpleHomesGameTest {
	@GameTest
	public void allHomeCommandsAreRegistered(GameTestHelper helper) {
		SimpleHomesGameTests.allHomeCommandsAreRegistered(helper);
	}

	@GameTest
	public void namedHomesRespectLimitsAndTeleport(GameTestHelper helper) {
		SimpleHomesGameTests.namedHomesRespectLimitsAndTeleport(helper);
	}

	@GameTest
	public void homeSharingIsOwnerAndHomeScoped(GameTestHelper helper) {
		SimpleHomesGameTests.homeSharingIsOwnerAndHomeScoped(helper);
	}
}
