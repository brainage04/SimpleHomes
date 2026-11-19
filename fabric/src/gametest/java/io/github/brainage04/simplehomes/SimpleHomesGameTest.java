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

    @GameTest
    public void messagesUseModFeedbackAndHelpIsRegistered(GameTestHelper helper) {
        SimpleHomesGameTests.messagesUseModFeedbackAndHelpIsRegistered(helper);
    }

    @GameTest
    public void homesLandWhereThePlayerStood(GameTestHelper helper) {
        SimpleHomesGameTests.homesLandWhereThePlayerStood(helper);
    }

    @GameTest
    public void obstructedHomesFailWithoutMovingThePlayer(GameTestHelper helper) {
        SimpleHomesGameTests.obstructedHomesFailWithoutMovingThePlayer(helper);
    }

    @GameTest
    public void homeofFindsOwnersWithoutAskingMojang(GameTestHelper helper) {
        SimpleHomesGameTests.homeofFindsOwnersWithoutAskingMojang(helper);
    }

    @GameTest
    public void ownersRevokeGuestsWhoAreOffline(GameTestHelper helper) {
        SimpleHomesGameTests.ownersRevokeGuestsWhoAreOffline(helper);
    }

    @GameTest
    public void homesSavedWithOnlyABlockLandOnItsFloor(GameTestHelper helper) {
        SimpleHomesGameTests.homesSavedWithOnlyABlockLandOnItsFloor(helper);
    }

    @GameTest
    public void homesInScaffoldingOrPowderSnowAreJudgedWhereTheyAre(GameTestHelper helper) {
        SimpleHomesGameTests.homesInScaffoldingOrPowderSnowAreJudgedWhereTheyAre(helper);
    }

    @GameTest
    public void playerOnlyCommandsAnswerNonPlayersThroughModFeedback(GameTestHelper helper) {
        SimpleHomesGameTests.playerOnlyCommandsAnswerNonPlayersThroughModFeedback(helper);
    }

    @GameTest
    public void commandsClickForThePlayerThroughModFeedback(GameTestHelper helper) {
        SimpleHomesGameTests.commandsClickForThePlayerThroughModFeedback(helper);
    }

    @GameTest
    public void revokeIsRefusedAsANewHomeName(GameTestHelper helper) {
        SimpleHomesGameTests.revokeIsRefusedAsANewHomeName(helper);
    }

    @GameTest
    public void aHomeSavedUnderTheReservedNameStaysUsable(GameTestHelper helper) {
        SimpleHomesGameTests.aHomeSavedUnderTheReservedNameStaysUsable(helper);
    }

    @GameTest
    public void sharehomeRevokeSuggestsHomeNamesBeforeAnythingIsTyped(GameTestHelper helper) {
        SimpleHomesGameTests.sharehomeRevokeSuggestsHomeNamesBeforeAnythingIsTyped(helper);
    }
}
