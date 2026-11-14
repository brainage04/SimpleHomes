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
    public void aHomeInWaterStillTeleports(GameTestHelper helper) {
        SimpleHomesGameTests.aHomeInWaterStillTeleports(helper);
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

    // The GameTest server ticks as fast as it can, so a tick budget cannot bound a wait for a chunk
    // load (20000 ticks took 3 s on one host and 90 s on another, 14 Nov 2026). These tests check
    // 2 min of wall time themselves, and the budget is only a backstop above that.
    @GameTest(maxTicks = 1000000)
    public void homesInUnloadedChunksTeleportAfterLoadingThem(GameTestHelper helper) {
        SimpleHomesGameTests.homesInUnloadedChunksTeleportAfterLoadingThem(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aHomeInsideTheGroundOfAnUnloadedChunkStillFails(GameTestHelper helper) {
        SimpleHomesGameTests.aHomeInsideTheGroundOfAnUnloadedChunkStillFails(helper);
    }

    @GameTest
    public void vanillaTpLeavesTheDestinationChunkUnloaded(GameTestHelper helper) {
        SimpleHomesGameTests.vanillaTpLeavesTheDestinationChunkUnloaded(helper);
    }

    @GameTest
    public void homesInLavaOrOverTheVoidFailWithoutMovingThePlayer(GameTestHelper helper) {
        SimpleHomesGameTests.homesInLavaOrOverTheVoidFailWithoutMovingThePlayer(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aHomeInLavaInAnUnloadedChunkFailsOnceTheChunkLoads(GameTestHelper helper) {
        SimpleHomesGameTests.aHomeInLavaInAnUnloadedChunkFailsOnceTheChunkLoads(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void anOldBlockOnlyHomeInAnUnloadedChunkLandsOnItsBlock(GameTestHelper helper) {
        SimpleHomesGameTests.anOldBlockOnlyHomeInAnUnloadedChunkLandsOnItsBlock(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aSecondHomeRequestWhileTheChunkLoadsWaitsForTheLoadToo(GameTestHelper helper) {
        SimpleHomesGameTests.aSecondHomeRequestWhileTheChunkLoadsWaitsForTheLoadToo(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds(GameTestHelper helper) {
        SimpleHomesGameTests.aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aTicketAnotherModTookAwayIsTakenAgainBeforeArriving(GameTestHelper helper) {
        SimpleHomesGameTests.aTicketAnotherModTookAwayIsTakenAgainBeforeArriving(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aFailedArrivalIsReportedAndGivesTheTicketBack(GameTestHelper helper) {
        SimpleHomesGameTests.aFailedArrivalIsReportedAndGivesTheTicketBack(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack(GameTestHelper helper) {
        SimpleHomesGameTests.aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne(GameTestHelper helper) {
        SimpleHomesGameTests.aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne(helper);
    }
}
