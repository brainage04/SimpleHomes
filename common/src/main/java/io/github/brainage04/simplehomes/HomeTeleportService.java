package io.github.brainage04.simplehomes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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

    /**
     * The deepest the drop check looks for a floor, far past any safe fall distance a player has
     * without an operator or a mod changing it (Attributes.java:86-87 allows up to 1024).
     */
    private static final double MAX_DROP = 128.0;

    private HomeTeleportService() {}

    public static int teleport(ServerPlayer player, HomeLocation home, String label) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, home.dimension());
        ServerLevel level = player.level().getServer().getLevel(dimension);
        if (level == null) {
            SimpleHomes.FEEDBACK.error(player, "The dimension for %s is unavailable.", label);
            return 0;
        }
        Vec3 destination = home.destinationWithoutWorld();
        if (chunksToLoad(level, player, destination).isEmpty()) {
            return arrive(player, level, home, label) ? 1 : 0;
        }
        // The checks below read blocks, so the chunk has to be loaded first, and loading it here
        // would generate it on the server thread. A ticket loads it off the server thread
        // instead, as vanilla's own spawn search does for a chunk it must inspect
        // (PlayerSpawnFinder.java:114-121); the answer reaches the player from the callback a few
        // ticks later.
        SimpleHomes.FEEDBACK.neutral(player, "Loading %s...", label);
        loadThenArrive(
                level, player, destination, label, 1, () -> arrive(player, level, home, label));
        return 1;
    }

    /**
     * Loads every chunk under the standing box at {@code destination}, then runs {@code arrive} on
     * the server thread once they are all full, unless the player has left by then. Package private
     * for the GameTests.
     */
    static void loadThenArrive(
            ServerLevel level,
            ServerPlayer player,
            Vec3 destination,
            String label,
            int attempt,
            Runnable arrive) {
        List<ChunkPos> chunks = chunksUnder(player, destination);
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
                                    // The next attempt loads every chunk again, not only the
                                    // missing ones: the release below would otherwise take the
                                    // ticket off a chunk that is still there.
                                    if (chunksToLoad(level, player, destination).isEmpty()) {
                                        arrive.run();
                                    } else if (attempt < LOAD_ATTEMPTS) {
                                        loadThenArrive(
                                                level,
                                                player,
                                                destination,
                                                label,
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
                        // Queued, never run at once: the server's executor runs a task at once on
                        // the server thread (BlockableEventLoop.java:98-105), and a load that loses
                        // its ticket completes inside DistanceManager.runAllUpdates
                        // (DistanceManager.java:76-85), which loading again would re-enter.
                        task -> level.getServer().schedule(level.getServer().wrapRunnable(task)));
    }

    private static boolean arrive(
            ServerPlayer player, ServerLevel level, HomeLocation home, String label) {
        Vec3 destination = home.destination(level);
        Deadly deadly = deadlyAt(level, player, destination);
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
        if (dropHurts(level, player, destination)) {
            SimpleHomes.FEEDBACK.error(
                    player,
                    "Could not teleport to %s because it is in the air above a long drop.",
                    label);
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
     * The chunks under the standing box at {@code destination}. Nothing here reads a block: a floor
     * lookup needs the chunk, which is why the destination used for this question is the one that
     * needs no world ({@link HomeLocation#destinationWithoutWorld()}).
     */
    private static List<ChunkPos> chunksUnder(ServerPlayer player, Vec3 destination) {
        AABB standing = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = SectionPos.posToSectionCoord(standing.minX);
                x <= SectionPos.posToSectionCoord(standing.maxX);
                x++) {
            for (int z = SectionPos.posToSectionCoord(standing.minZ);
                    z <= SectionPos.posToSectionCoord(standing.maxZ);
                    z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    /** The chunks under the standing box at {@code destination} that are not full. */
    private static List<ChunkPos> chunksToLoad(
            ServerLevel level, ServerPlayer player, Vec3 destination) {
        List<ChunkPos> unloaded = new ArrayList<>();
        for (ChunkPos chunk : chunksUnder(player, destination)) {
            // Not hasChunk: it is already true while another teleport's ticket is still loading the
            // chunk (ServerChunkCache.java:257-262), and reading a block there waits for the load
            // on the server thread (ServerChunkCache.java:143-172). getChunkNow answers only once
            // the chunk is full (ServerChunkCache.java:175-203).
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                unloaded.add(chunk);
            }
        }
        return unloaded;
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

    /**
     * Why the destination cannot be used: it is in lava, or it is over the void. Vanilla's own
     * answer for a safe teleport destination is SpreadPlayersCommand.Pos.isSafe
     * (SpreadPlayersCommand.java:364-368): the first block from the destination down to the level's
     * minimum build height must exist, found by the scan in getSpawnY
     * (SpreadPlayersCommand.java:344-362), and must not be a liquid. Running out of blocks before
     * the minimum build height is the void. Lava is the liquid that kills, the one vanilla's lava
     * damage names (FluidTags.LAVA, Entity.java:1772-1774); water is survivable, so a home in it is
     * still usable. Lava hurts the player wherever their box touches it, which the column below the
     * centre of their feet can miss next to a lava pool: Entity.checkInsideBlocks visits each block
     * under the box shrunk by 1e-5 (Entity.java:1346-1354), and LavaFluid.entityInside deals the
     * damage (LavaFluid.java:120-124) when the box overlaps that block's fluid (Entity.java:1376,
     * 1394-1396, 1426-1435).
     */
    private static Deadly deadlyAt(ServerLevel level, ServerPlayer player, Vec3 destination) {
        DamageSource lavaDamage = level.damageSources().lava();
        // Lava does not hurt a creative or spectator player (Player.java:677-679), who only sinks
        // through it; the void's damage gets past that (DamageTypeTags.BYPASSES_INVULNERABILITY).
        // Fire Resistance stops fire damage, lava's included (LivingEntity.java:1187-1189), but
        // only while it lasts, and the player may still be in the lava when it ends.
        MobEffectInstance fireResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
        boolean lavaHurts =
                (!player.getAbilities().invulnerable
                                || lavaDamage.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
                        && !(fireResistance != null
                                && fireResistance.isInfiniteDuration()
                                && lavaDamage.is(DamageTypeTags.IS_FIRE));
        // The largest of the player's poses (Avatar.java:24-36): a player who crouches or crawls
        // now can stand up there, into lava that their present box would miss.
        AABB box = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        for (BlockPos pos : BlockPos.betweenClosed(box.deflate(1.0E-5F))) {
            FluidState fluid = level.getFluidState(pos);
            if (lavaHurts
                    && fluid.is(FluidTags.LAVA)
                    && box.collidedAlongVector(Vec3.ZERO, List.of(fluid.getAABB(level, pos)))) {
                return Deadly.LAVA;
            }
        }
        BlockPos feet = BlockPos.containing(destination);
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int y = feet.getY(); y >= level.getMinY(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            boolean lava = state.getFluidState().is(FluidTags.LAVA);
            if (state.isAir() || (lava && !lavaHurts)) continue;
            return lava ? Deadly.LAVA : Deadly.NONE;
        }
        return Deadly.VOID;
    }

    /**
     * Whether the fall from {@code destination} to the floor below would hurt the player, by the
     * game's rule: damage is floor((fall + 1e-6 - safe_fall_distance) * modifier *
     * fall_damage_multiplier) (LivingEntity.java:1851-1862), and a landing without a fall deals
     * none (Entity.java:1589-1590). Every guess errs towards refusing:
     *
     * <ul>
     *   <li>The floor is the first block collision below the standing box, judged at the
     *       destination height as {@link #fits} judges it.
     *   <li>Water, a block tagged as resetting fall damage and powder snow reset the fall
     *       (Entity.java:768-775, 1680-1684, 3030-3032). A landing in one deals nothing, and below
     *       one the fall counts from its top, above every point where the game can reset it.
     *   <li>A stalagmite tip anywhere under the box adds 2.5 blocks and doubles the damage
     *       (PointedDripstoneBlock.java:53-60). Blocks that soften a landing, Slow Falling and
     *       entities to stand on get no credit.
     *   <li>No floor within the reach counts as hurting: a fall that deep hurts, and past {@link
     *       #MAX_DROP} it may go on any distance.
     * </ul>
     */
    private static boolean dropHurts(ServerLevel level, ServerPlayer player, Vec3 destination) {
        // A player who may fly takes no fall damage (Player.java:1437-1441), and a creative or
        // spectator player cannot be hurt by it (Player.java:677-679).
        if (player.getAbilities().mayfly || player.getAbilities().invulnerable) {
            return false;
        }
        double safeDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        double multiplier = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        double reach = Math.clamp(Math.ceil(safeDistance + 1.0 / multiplier) + 1.0, 1.0, MAX_DROP);
        AABB box = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        Iterable<VoxelShape> below =
                level.getBlockCollisionsFromContext(
                        CollisionContext.positionContext(destination.y()),
                        box.setMinY(box.minY - reach).setMaxY(box.minY));
        double fall = -Shapes.collide(Direction.Axis.Y, box, below, -reach);
        if (fall >= reach) {
            return true;
        }
        double floor = box.minY - fall;
        double counted = fall;
        Vec3 centre = box.getCenter();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double y = box.minY - 1.0E-4; y > floor; y -= 1.0) {
            pos.set(centre.x, y, centre.z);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER)
                    || state.is(BlockTags.FALL_DAMAGE_RESETTING)
                    || state.getBlock() instanceof PowderSnowBlock) {
                if (pos.getY() <= floor) {
                    return false;
                }
                counted = Math.min(counted, pos.getY() + 1.0 - floor);
            }
        }
        if (counted <= 0.0) {
            return false;
        }
        double added = 0.0;
        double modifier = 1.0;
        int floorY = Mth.floor(floor - 1.0E-6);
        for (BlockPos under :
                BlockPos.betweenClosed(
                        BlockPos.containing(box.minX, floorY, box.minZ),
                        BlockPos.containing(box.maxX, floorY, box.maxZ))) {
            BlockState state = level.getBlockState(under);
            if (state.getBlock() instanceof PointedDripstoneBlock
                    && state.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.UP
                    && state.getValue(SpeleothemBlock.THICKNESS) == SpeleothemThickness.TIP) {
                // The numbers PointedDripstoneBlock.fallOn passes (PointedDripstoneBlock.java:56),
                // which no API exposes.
                added = 2.5;
                modifier = 2.0;
            }
        }
        return Mth.floor((counted + added + 1.0E-6 - safeDistance) * modifier * multiplier) > 0;
    }

    private enum Deadly {
        NONE,
        LAVA,
        VOID
    }
}
