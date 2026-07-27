package io.github.brainage04.simplehomes;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.Set;

public final class HomeTeleportService {
	private HomeTeleportService() {
	}

	public static int teleport(ServerPlayer player, HomeLocation home, String label) {
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, home.dimension());
		ServerLevel level = player.level().getServer().getLevel(dimension);
		if (level == null) {
			player.sendSystemMessage(Component.literal("The dimension for " + label + " is unavailable."));
			return 0;
		}
		player.teleportTo(
				level,
				home.position().getX() + 0.5D,
				home.position().getY(),
				home.position().getZ() + 0.5D,
				Set.of(),
				home.yaw(),
				home.pitch(),
				false
		);
		player.sendSystemMessage(Component.literal("Teleported to " + label + "."));
		return 1;
	}
}
