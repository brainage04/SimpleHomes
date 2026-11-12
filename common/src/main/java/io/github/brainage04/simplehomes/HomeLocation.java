package io.github.brainage04.simplehomes;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record HomeLocation(Identifier dimension, Vec3 position, float yaw, float pitch) {
    // Homes saved by earlier builds hold only the block under "position"; they keep landing on that
    // block's bottom centre, as those builds did.
    private static final MapCodec<Vec3> POSITION_CODEC =
            Codec.mapEither(Vec3.CODEC.fieldOf("pos"), BlockPos.CODEC.fieldOf("position"))
                    .xmap(either -> either.map(pos -> pos, Vec3::atBottomCenterOf), Either::left);

    public static final Codec<HomeLocation> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Identifier.CODEC
                                                    .fieldOf("dimension")
                                                    .forGetter(HomeLocation::dimension),
                                            POSITION_CODEC.forGetter(HomeLocation::position),
                                            Codec.FLOAT
                                                    .optionalFieldOf("yaw", 0.0F)
                                                    .forGetter(HomeLocation::yaw),
                                            Codec.FLOAT
                                                    .optionalFieldOf("pitch", 0.0F)
                                                    .forGetter(HomeLocation::pitch))
                                    .apply(instance, HomeLocation::new));

    public static HomeLocation from(ServerPlayer player) {
        return new HomeLocation(
                player.level().dimension().identifier(),
                player.position(),
                player.getYRot(),
                player.getXRot());
    }
}
