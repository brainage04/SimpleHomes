package io.github.brainage04.simplehomes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public record HomeLocation(Identifier dimension, BlockPos position, float yaw, float pitch) {
	public static final Codec<HomeLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("dimension").forGetter(HomeLocation::dimension),
			BlockPos.CODEC.fieldOf("position").forGetter(HomeLocation::position),
			Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(HomeLocation::yaw),
			Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(HomeLocation::pitch)
	).apply(instance, HomeLocation::new));

	public static HomeLocation from(ServerPlayer player) {
		return new HomeLocation(
				player.level().dimension().identifier(),
				player.blockPosition().immutable(),
				player.getYRot(),
				player.getXRot()
		);
	}
}
