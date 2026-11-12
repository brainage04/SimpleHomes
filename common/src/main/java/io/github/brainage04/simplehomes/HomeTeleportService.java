package io.github.brainage04.simplehomes;

import java.util.Set;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HomeTeleportService {
    private HomeTeleportService() {}

    public static int teleport(ServerPlayer player, HomeLocation home, String label) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, home.dimension());
        ServerLevel level = player.level().getServer().getLevel(dimension);
        if (level == null) {
            SimpleHomes.FEEDBACK.error(player, "The dimension for %s is unavailable.", label);
            return 0;
        }
        Vec3 destination = home.position();
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
     * Whether the player fits at {@code destination} in one of the poses vanilla tries when it puts
     * a player down from a vehicle: the same block-collision and world-border test
     * (DismountHelper.canDismountTo, Player.getDismountPoses). Liquids do not count, as in that
     * test.
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
        return player.getDismountPoses().stream()
                .anyMatch(pose -> DismountHelper.canDismountTo(level, destination, player, pose));
    }
}
