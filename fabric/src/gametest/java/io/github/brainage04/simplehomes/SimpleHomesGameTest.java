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

	@GameTest
	public void commandsNormalizeNamesAndEnforceLimits(GameTestHelper helper) {
		SimpleHomesGameTests.commandsNormalizeNamesAndEnforceLimits(helper);
	}

	@GameTest
	public void sharingCommandsEnforceAndRevokeAccess(GameTestHelper helper) {
		SimpleHomesGameTests.sharingCommandsEnforceAndRevokeAccess(helper);
	}

	@GameTest
	public void teleportsAcrossDimensionsAndRejectsMissingDimensions(GameTestHelper helper) {
		SimpleHomesGameTests.teleportsAcrossDimensionsAndRejectsMissingDimensions(helper);
	}

	@GameTest
	public void rejectedTeleportDoesNotReportSuccess(GameTestHelper helper) {
		SimpleHomesGameTests.rejectedTeleportDoesNotReportSuccess(helper);
	}
}
