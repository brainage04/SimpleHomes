package io.github.brainage04.simplehomes;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HomeTeleportService {
    private HomeTeleportService() {}

    public static int teleport(ServerPlayer player, HomeLocation home, String label) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, home.dimension());
        ServerLevel level = player.level().getServer().getLevel(dimension);
        if (level == null) {
            SimpleHomes.FEEDBACK.error(player, "The dimension for %s is unavailable.", label);
            return 0;
        }
        Vec3 destination = home.destination(level);
        Deadly deadly = deadlyAt(level, destination);
        if (deadly != Deadly.NONE) {
            SimpleHomes.FEEDBACK.error(
                    player,
                    deadly == Deadly.LAVA
                            ? "Could not teleport to %s because it is in lava."
                            : "Could not teleport to %s because it is over the void.",
                    label);
            return 0;
        }
        if (!fits(level, player, destination)) {
            SimpleHomes.FEEDBACK.error(
                    player, "Could not teleport to %s because it is obstructed.", label);
            return 0;
        }
        boolean teleported =
                player.teleportTo(
                        level,
                        destination.x(),
                        destination.y(),
                        destination.z(),
                        Set.of(),
                        home.yaw(),
                        home.pitch(),
                        false);
        if (!teleported) {
            SimpleHomes.FEEDBACK.error(player, "Could not teleport to %s.", label);
            return 0;
        }
        SimpleHomes.FEEDBACK.success(player, "Teleported to %s.", label);
        return 1;
    }

    /**
     * Why the destination cannot be used: it is in lava, or it is over the void. Vanilla's own
     * answer for a safe teleport destination is SpreadPlayersCommand.Pos.isSafe
     * (SpreadPlayersCommand.java:364-368): the first block from the destination down to the
     * level's minimum build height must exist, found by the scan in getSpawnY
     * (SpreadPlayersCommand.java:344-362), and must not be a liquid. Running out of blocks before
     * the minimum build height is the void. Lava is the liquid that kills, the one vanilla's lava
     * damage names (FluidTags.LAVA, Entity.java:1772-1774); water is survivable, so a home in it is
     * still usable.
     */
    private static Deadly deadlyAt(ServerLevel level, Vec3 destination) {
        BlockPos feet = BlockPos.containing(destination);
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int y = feet.getY(); y >= level.getMinY(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            return state.getFluidState().is(FluidTags.LAVA) ? Deadly.LAVA : Deadly.NONE;
        }
        return Deadly.VOID;
    }

    private enum Deadly {
        NONE,
        LAVA,
        VOID
    }

    /**
     * Whether the player fits at {@code destination}: vanilla's check for a passenger getting off a
     * mount (DismountHelper.canDismountTo, DismountHelper.java:42-50), block collisions and the
     * world border in any of the player's dismount poses (Player.getDismountPoses), judged at the
     * destination height. Liquids do not count, as in that check.
     */
    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 destination) {
        // Block collisions treat a chunk that is not loaded as empty (BlockCollisions.java:86-87,
        // Level.getChunkForCollisions), so load the chunks under the standing box first.
        AABB standing = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        for (int x = SectionPos.posToSectionCoord(standing.minX);
                x <= SectionPos.posToSectionCoord(standing.maxX);
                x++) {
            for (int z = SectionPos.posToSectionCoord(standing.minZ);
                    z <= SectionPos.posToSectionCoord(standing.maxZ);
                    z++) {
                level.getChunk(x, z);
            }
        }
        // canDismountTo uses the player's own collision context, which shapes scaffolding and
        // powder snow by where the player stands now (ScaffoldingBlock.java:130-139,
        // PowderSnowBlock.java:116-131). A dismount happens at about that height and a teleport
        // does not, so this context judges them at the destination height. It has no entity, so
        // powder snow never collides, whatever boots the player wears.
        CollisionContext atDestination = CollisionContext.positionContext(destination.y());
        return player.getDismountPoses().stream()
                .map(pose -> player.getLocalBoundsForPose(pose).move(destination))
                .anyMatch(
                        box ->
                                noBlockCollision(level, atDestination, box)
                                        && level.getWorldBorder().isWithinBounds(box));
    }

    private static boolean noBlockCollision(ServerLevel level, CollisionContext context, AABB box) {
        for (VoxelShape collision : level.getBlockCollisionsFromContext(context, box)) {
            if (!collision.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
