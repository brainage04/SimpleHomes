package io.github.brainage04.simplehomes;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * A saved home. {@code position} is the exact position the player stood at, or, for a home saved by
 * a build that stored only the block the feet were in, that block.
 */
public record HomeLocation(
        Identifier dimension, Either<Vec3, BlockPos> position, float yaw, float pitch) {
    public static final Codec<HomeLocation> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Identifier.CODEC
                                                    .fieldOf("dimension")
                                                    .forGetter(HomeLocation::dimension),
                                            Codec.mapEither(
                                                            Vec3.CODEC.fieldOf("pos"),
                                                            BlockPos.CODEC.fieldOf("position"))
                                                    .forGetter(HomeLocation::position),
                                            Codec.FLOAT
                                                    .optionalFieldOf("yaw", 0.0F)
                                                    .forGetter(HomeLocation::yaw),
                                            Codec.FLOAT
                                                    .optionalFieldOf("pitch", 0.0F)
                                                    .forGetter(HomeLocation::pitch))
                                    .apply(instance, HomeLocation::new));

    public HomeLocation(Identifier dimension, Vec3 position, float yaw, float pitch) {
        this(dimension, Either.left(position), yaw, pitch);
    }

    public static HomeLocation from(ServerPlayer player) {
        return new HomeLocation(
                player.level().dimension().identifier(),
                player.position(),
                player.getYRot(),
                player.getXRot());
    }

    /**
     * Where the player lands in {@code level}: the exact position, or for a home that holds only a
     * block, on top of the floor in that block, by vanilla's floor-height rule
     * (BlockGetter.getBlockFloorHeight, BlockGetter.java:96-110), as /spawn in SpawnCommands does.
     */
    public Vec3 destination(BlockGetter level) {
        return position.map(exact -> exact, block -> onFloorOf(level, block));
    }

    private static Vec3 onFloorOf(BlockGetter level, BlockPos block) {
        double floor = level.getBlockFloorHeight(block);
        // No floor within reach (the block and the one below have no full-height top): the
        // block's bottom centre, where the builds that saved only the block landed.
        return Vec3.upFromBottomCenterOf(block, Double.isInfinite(floor) ? 0.0D : floor);
    }
}
