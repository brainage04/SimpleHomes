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
                "homes_in_unloaded_chunks_teleport_like_vanilla",
                SimpleHomesGameTests::homesInUnloadedChunksTeleportLikeVanilla);
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
