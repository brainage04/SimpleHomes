package io.github.brainage04.simplehomes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HomeTeleportService {
    /**
     * How many pending teleports hold a ticket on each chunk. Tickets of one type and level are a
     * single entry (TicketStorage.java:147-156, 223-240), so giving back the ticket when one
     * teleport ends would take it away from another teleport still waiting on the same chunk. Only
     * read and written on the server thread; a level that is gone takes its counts with it.
     */
    private static final Map<ServerLevel, Map<ChunkPos, Integer>> TICKETS = new WeakHashMap<>();

    /** How many times a teleport loads its chunks before it gives up on them staying loaded. */
    private static final int LOAD_ATTEMPTS = 3;

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
        // ticks later.
        SimpleHomes.FEEDBACK.neutral(player, "Loading %s...", label);
        Vec3 destination = home.destinationWithoutWorld();
        loadThenArrive(
                level,
                player,
                destination,
                label,
                unloaded,
                1,
                () -> arrive(player, level, home, label));
        return 1;
    }

    /**
     * Loads {@code chunks}, then runs {@code arrive} on the server thread once every chunk under
     * the standing box at {@code destination} is full, unless the player has left by then. Package
     * private for the GameTests.
     */
    static void loadThenArrive(
            ServerLevel level,
            ServerPlayer player,
            Vec3 destination,
            String label,
            List<ChunkPos> chunks,
            int attempt,
            Runnable arrive) {
        load(level, chunks)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            try {
                                if (failure != null) {
                                    SimpleHomes.FEEDBACK.error(
                                            player, "Could not teleport to %s.", label);
                                } else if (!player.hasDisconnected()) {
                                    // The count above is this mod's own: another mod that adds and
                                    // removes a PLAYER_LOADING ticket on the same chunk shares the
                                    // one ticket (TicketStorage.java:147-156) and can take it away
                                    // before this load is done, which completes the load with an
                                    // unloaded chunk (GenerationChunkHolder.java:83-91, 164-167).
                                    List<ChunkPos> missing =
                                            chunksToLoad(level, player, destination);
                                    if (missing.isEmpty()) {
                                        arrive.run();
                                    } else if (attempt < LOAD_ATTEMPTS) {
                                        loadThenArrive(
                                                level,
                                                player,
                                                destination,
                                                label,
                                                missing,
                                                attempt + 1,
                                                arrive);
                                    } else {
                                        SimpleHomes.FEEDBACK.error(
                                                player,
                                                "Could not teleport to %s because its chunk would"
                                                        + " not stay loaded.",
                                                label);
                                    }
                                }
                            } catch (Throwable thrown) {
                                SimpleHomes.LOGGER.error(
                                        "Could not teleport {} to {} at {} in {}",
                                        player.getScoreboardName(),
                                        label,
                                        destination,
                                        level.dimension().identifier(),
                                        thrown);
                                SimpleHomes.FEEDBACK.error(
                                        player, "Could not teleport to %s.", label);
                            } finally {
                                release(level, chunks);
                            }
                        },
                        level.getServer());
    }

    private static boolean arrive(
            ServerPlayer player, ServerLevel level, HomeLocation home, String label) {
        Vec3 destination = home.destination(level);
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
                // Not hasChunk: it is already true while another teleport's ticket is still
                // loading the chunk (ServerChunkCache.java:257-262), and reading a block there
                // waits for the load on the server thread (ServerChunkCache.java:143-172).
                // getChunkNow answers only once the chunk is full (ServerChunkCache.java:175-203).
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    unloaded.add(new ChunkPos(x, z));
                }
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
    static CompletableFuture<?> load(ServerLevel level, List<ChunkPos> chunks) {
        Map<ChunkPos, Integer> held = TICKETS.computeIfAbsent(level, ignored -> new HashMap<>());
        CompletableFuture<?>[] loads = new CompletableFuture<?>[chunks.size()];
        for (int i = 0; i < loads.length; i++) {
            ChunkPos pos = chunks.get(i);
            held.merge(pos, 1, Integer::sum);
            loads[i] =
                    level.getChunkSource()
                            .addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, pos, 0);
        }
        return CompletableFuture.allOf(loads);
    }

    /**
     * Gives back the tickets {@link #load} took, so they do not keep the chunks loaded, once no
     * other pending teleport still needs them.
     */
    static void release(ServerLevel level, List<ChunkPos> chunks) {
        Map<ChunkPos, Integer> held = TICKETS.get(level);
        for (ChunkPos pos : chunks) {
            if (held.merge(pos, -1, Integer::sum) == 0) {
                held.remove(pos);
                level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
            }
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
}
