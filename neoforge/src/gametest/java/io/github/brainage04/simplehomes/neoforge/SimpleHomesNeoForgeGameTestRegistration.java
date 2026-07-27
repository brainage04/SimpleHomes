package io.github.brainage04.simplehomes.neoforge;

import io.github.brainage04.simplehomes.SimpleHomes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = SimpleHomes.MOD_ID)
public final class SimpleHomesNeoForgeGameTestRegistration {
	private SimpleHomesNeoForgeGameTestRegistration() {
	}

	@SubscribeEvent
	public static void registerTestFunctions(RegisterEvent event) {
		SimpleHomesNeoForgeGameTests tests = new SimpleHomesNeoForgeGameTests();
		register(event, "all_home_commands_are_registered", tests::allHomeCommandsAreRegistered);
		register(event, "named_homes_respect_limits_and_teleport", tests::namedHomesRespectLimitsAndTeleport);
		register(event, "home_sharing_is_owner_and_home_scoped", tests::homeSharingIsOwnerAndHomeScoped);
	}

	private static void register(
			RegisterEvent event,
			String id,
			java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test
	) {
		event.register(BuiltInRegistries.TEST_FUNCTION.key(), SimpleHomes.of(id), () -> test);
	}
}
