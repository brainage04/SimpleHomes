package io.github.brainage04.simplehomes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
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
        List<ChunkPos> unloaded = chunksToLoad(level, player, home.destinationWithoutWorld());
        if (unloaded.isEmpty()) {
            return arrive(player, level, home, label) ? 1 : 0;
        }
        // The checks below read blocks, so the chunk has to be loaded first, and loading it here
        // would generate it on the server thread. A ticket loads it off the server thread
        // instead, as vanilla's own spawn search does for a chunk it must inspect
        // (PlayerSpawnFinder.java:114-121); the answer reaches the player from the callback a few
        // ticks later, and the command reports that it was accepted.
        load(level, unloaded)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            try {
                                if (failure != null) {
                                    SimpleHomes.FEEDBACK.error(
                                            player, "Could not teleport to %s.", label);
                                } else if (!player.hasDisconnected()) {
                                    arrive(player, level, home, label);
                                }
                            } finally {
                                release(level, unloaded);
                            }
                        },
                        level.getServer());
        return 1;
    }

    private static boolean arrive(
            ServerPlayer player, ServerLevel level, HomeLocation home, String label) {
        Vec3 destination = home.destination(level);
        Deadly deadly = deadlyAt(level, destination);
        if (deadly != Deadly.NONE) {
            SimpleHomes.FEEDBACK.error(
                    player,
                    deadly == Deadly.LAVA
                            ? "Could not teleport to %s because it is in lava."
                            : "Could not teleport to %s because it is over the void.",
                    label);
            return false;
        }
        if (!fits(level, player, destination)) {
            SimpleHomes.FEEDBACK.error(
                    player, "Could not teleport to %s because it is obstructed.", label);
            return false;
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
            return false;
        }
        SimpleHomes.FEEDBACK.success(player, "Teleported to %s.", label);
        return true;
    }

    /**
     * The chunks under the standing box at {@code destination} that are not loaded. Nothing here
     * reads a block: a floor lookup needs the chunk, which is why the destination used for this
     * question is the one that needs no world ({@link HomeLocation#destinationWithoutWorld()}).
     */
    private static List<ChunkPos> chunksToLoad(
            ServerLevel level, ServerPlayer player, Vec3 destination) {
        AABB standing = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        Set<ChunkPos> unloaded = new LinkedHashSet<>();
        for (int x = SectionPos.posToSectionCoord(standing.minX);
                x <= SectionPos.posToSectionCoord(standing.maxX);
                x++) {
            for (int z = SectionPos.posToSectionCoord(standing.minZ);
                    z <= SectionPos.posToSectionCoord(standing.maxZ);
                    z++) {
                if (!level.hasChunk(x, z)) unloaded.add(new ChunkPos(x, z));
            }
        }
        return new ArrayList<>(unloaded);
    }

    /**
     * Loads {@code chunks} to full without blocking the server thread, and returns the futures that
     * complete when they are there (ServerChunkCache.addTicketAndLoadWithRadius,
     * ServerChunkCache.java:479-495). PLAYER_LOADING is the ticket type a player's own chunk
     * position uses and never expires (TicketType.java:20), so the load cannot be abandoned half
     * way; SPAWN_SEARCH expires after one tick (TicketType.java:18) and a chunk that is ready for
     * saving then loses it (TicketStorage.java:291-300) long before a cold chunk has generated,
     * which leaves the future pending forever. {@link #release} gives the ticket back.
     */
    private static CompletableFuture<?> load(ServerLevel level, List<ChunkPos> chunks) {
        CompletableFuture<?>[] loads =
                chunks.stream()
                        .map(
                                pos ->
                                        level.getChunkSource()
                                                .addTicketAndLoadWithRadius(
                                                        TicketType.PLAYER_LOADING, pos, 0))
                        .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(loads);
    }

    /** Gives back the tickets {@link #load} took, so they do not keep the chunks loaded. */
    private static void release(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos pos : chunks) {
            level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
        }
    }

    /**
     * Whether the player fits at {@code destination}: vanilla's check for a passenger getting off a
     * mount (DismountHelper.canDismountTo, DismountHelper.java:42-50), block collisions and the
     * world border in any of the player's dismount poses (Player.getDismountPoses), judged at the
     * destination height. Liquids do not count, as in that check.
     */
    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 destination) {
        // Only called once every chunk under the box is loaded: block collisions treat a chunk that
        // is not loaded as empty (BlockCollisions.java:86-87, Level.getChunkForCollisions).
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

    /**
     * Why the destination cannot be used: it is in lava, or it is over the void. Vanilla's own
     * answer for a safe teleport destination is SpreadPlayersCommand.Pos.isSafe
     * (SpreadPlayersCommand.java:364-368): the first block from the destination down to the level's
     * minimum build height must exist, found by the scan in getSpawnY
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
}
