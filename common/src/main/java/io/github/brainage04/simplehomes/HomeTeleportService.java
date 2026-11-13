package io.github.brainage04.simplehomes;

import java.util.Set;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
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
        Vec3 destination = home.destinationWithoutWorld();
        if (chunksLoaded(level, player, destination)) {
            destination = home.destination(level);
            if (!fits(level, player, destination)) {
                SimpleHomes.FEEDBACK.error(
                        player, "Could not teleport to %s because it is obstructed.", label);
                return 0;
            }
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
     * Whether every chunk under the standing box at {@code destination} is loaded. Vanilla's /tp
     * does not load or generate the destination itself: it moves the player, and the player's own
     * position then adds the chunk tickets that generate the chunk (ServerChunkCache.move,
     * ServerChunkCache.java:513-520, calls ChunkMap.move, ChunkMap.java:1064-1101, which calls
     * DistanceManager.addPlayer, DistanceManager.java:109-116, and recentres the client's view in
     * applyChunkTrackingView, ChunkMap.java:1112-1122). Reading a block here would generate the
     * chunk on the server thread instead, so a chunk that nobody has loaded is left to the
     * teleport, like /tp.
     */
    private static boolean chunksLoaded(ServerLevel level, ServerPlayer player, Vec3 destination) {
        AABB standing = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        for (int x = SectionPos.posToSectionCoord(standing.minX);
                x <= SectionPos.posToSectionCoord(standing.maxX);
                x++) {
            for (int z = SectionPos.posToSectionCoord(standing.minZ);
                    z <= SectionPos.posToSectionCoord(standing.maxZ);
                    z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    /**
     * Whether the player fits at {@code destination}: vanilla's check for a passenger getting off a
     * mount (DismountHelper.canDismountTo, DismountHelper.java:42-50), block collisions and the
     * world border in any of the player's dismount poses (Player.getDismountPoses), judged at the
     * destination height. Liquids do not count, as in that check.
     */
    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 destination) {
        // Only called for loaded chunks: block collisions treat a chunk that is not loaded as
        // empty (BlockCollisions.java:86-87, Level.getChunkForCollisions).
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
