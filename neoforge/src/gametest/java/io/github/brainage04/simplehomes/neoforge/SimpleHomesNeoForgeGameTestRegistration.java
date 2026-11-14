package io.github.brainage04.simplehomes.neoforge;

import io.github.brainage04.simplehomes.SimpleHomes;
import io.github.brainage04.simplehomes.SimpleHomesGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = SimpleHomes.MOD_ID)
public final class SimpleHomesNeoForgeGameTestRegistration {
    private SimpleHomesNeoForgeGameTestRegistration() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        register(
                event,
                "all_home_commands_are_registered",
                SimpleHomesGameTests::allHomeCommandsAreRegistered);
        register(
                event,
                "named_homes_respect_limits_and_teleport",
                SimpleHomesGameTests::namedHomesRespectLimitsAndTeleport);
        register(
                event,
                "home_sharing_is_owner_and_home_scoped",
                SimpleHomesGameTests::homeSharingIsOwnerAndHomeScoped);
        register(
                event,
                "commands_normalize_names_and_enforce_limits",
                SimpleHomesGameTests::commandsNormalizeNamesAndEnforceLimits);
        register(
                event,
                "sharing_commands_enforce_and_revoke_access",
                SimpleHomesGameTests::sharingCommandsEnforceAndRevokeAccess);
        register(
                event,
                "teleports_across_dimensions_and_rejects_missing_dimensions",
                SimpleHomesGameTests::teleportsAcrossDimensionsAndRejectsMissingDimensions);
        register(
                event,
                "rejected_teleport_does_not_report_success",
                SimpleHomesGameTests::rejectedTeleportDoesNotReportSuccess);
        register(
                event,
                "messages_use_mod_feedback_and_help_is_registered",
                SimpleHomesGameTests::messagesUseModFeedbackAndHelpIsRegistered);
        register(
                event,
                "homes_land_where_the_player_stood",
                SimpleHomesGameTests::homesLandWhereThePlayerStood);
        register(
                event,
                "obstructed_homes_fail_without_moving_the_player",
                SimpleHomesGameTests::obstructedHomesFailWithoutMovingThePlayer);
        register(
                event,
                "homeof_finds_owners_without_asking_mojang",
                SimpleHomesGameTests::homeofFindsOwnersWithoutAskingMojang);
        register(
                event,
                "homes_saved_with_only_a_block_land_on_its_floor",
                SimpleHomesGameTests::homesSavedWithOnlyABlockLandOnItsFloor);
        register(
                event,
                "homes_in_scaffolding_or_powder_snow_are_judged_where_they_are",
                SimpleHomesGameTests::homesInScaffoldingOrPowderSnowAreJudgedWhereTheyAre);
        register(
                event,
                "homes_in_unloaded_chunks_teleport_after_loading_them",
                SimpleHomesGameTests::homesInUnloadedChunksTeleportAfterLoadingThem);
        register(
                event,
                "a_home_inside_the_ground_of_an_unloaded_chunk_still_fails",
                SimpleHomesGameTests::aHomeInsideTheGroundOfAnUnloadedChunkStillFails);
        // vanillaTpLeavesTheDestinationChunkUnloaded runs on Fabric only: on NeoForge the
        // destination chunk was loaded right after /tp in a GameTest run on 14 Nov 2026, and the
        // test describes vanilla's /tp, not this mod.
        register(
                event,
                "an_old_block_only_home_in_an_unloaded_chunk_lands_on_its_block",
                SimpleHomesGameTests::anOldBlockOnlyHomeInAnUnloadedChunkLandsOnItsBlock);
        register(
                event,
                "homes_in_lava_or_over_the_void_fail_without_moving_the_player",
                SimpleHomesGameTests::homesInLavaOrOverTheVoidFailWithoutMovingThePlayer);
        register(
                event,
                "a_home_in_lava_in_an_unloaded_chunk_fails_once_the_chunk_loads",
                SimpleHomesGameTests::aHomeInLavaInAnUnloadedChunkFailsOnceTheChunkLoads);
        register(
                event,
                "a_second_home_request_while_the_chunk_loads_waits_for_the_load_too",
                SimpleHomesGameTests::aSecondHomeRequestWhileTheChunkLoadsWaitsForTheLoadToo);
        register(
                event,
                "a_finished_load_leaves_the_ticket_another_load_still_needs",
                SimpleHomesGameTests::aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds);
        register(
                event,
                "a_ticket_another_mod_took_away_is_taken_again_before_arriving",
                SimpleHomesGameTests::aTicketAnotherModTookAwayIsTakenAgainBeforeArriving);
        register(
                event,
                "a_failed_arrival_is_reported_and_gives_the_ticket_back",
                SimpleHomesGameTests::aFailedArrivalIsReportedAndGivesTheTicketBack);
        register(
                event,
                "a_player_who_leaves_during_the_load_gives_the_ticket_back",
                SimpleHomesGameTests::aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack);
        register(
                event,
                "a_spot_over_two_chunks_loads_both_again_when_another_mod_takes_one",
                SimpleHomesGameTests::aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne);
        register(
                event,
                "owners_revoke_guests_who_are_offline",
                SimpleHomesGameTests::ownersRevokeGuestsWhoAreOffline);
    }

    private static void register(
            RegisterEvent event,
            String id,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), SimpleHomes.of(id), () -> test);
    }
}
