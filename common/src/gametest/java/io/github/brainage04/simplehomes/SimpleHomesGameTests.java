package io.github.brainage04.simplehomes;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.serialization.JsonOps;
import io.github.brainage04.brainagelib.gametest.CapturedCommandOutput;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class SimpleHomesGameTests {
    private SimpleHomesGameTests() {}

    public static void allHomeCommandsAreRegistered(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String command : List.of("sethome", "home", "homeof", "sharehome")) {
            if (root.getChild(command) == null)
                throw new AssertionError("Expected /" + command + " to be registered");
        }
        helper.succeed();
    }

    public static void namedHomesRespectLimitsAndTeleport(GameTestHelper helper) {
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            HomeData data = HomeData.get(helper.getLevel().getServer());
            BlockPos homePosition = helper.absolutePos(new BlockPos(2, 2, 2));
            HomeLocation home =
                    new HomeLocation(
                            helper.getLevel().dimension().identifier(),
                            Vec3.atBottomCenterOf(homePosition),
                            45.0F,
                            0.0F);
            helper.assertTrue(
                    data.setHome(player.getUUID(), "base", home, 1) == HomeData.SetResult.CREATED,
                    "The first named home must be created");
            helper.assertTrue(
                    data.setHome(player.getUUID(), "mine", home, 1)
                            == HomeData.SetResult.LIMIT_REACHED,
                    "A new home beyond the configured limit must be rejected");
            player.teleportTo(20.0D, homePosition.getY(), 20.0D);
            helper.assertTrue(
                    HomeTeleportService.teleport(player, home, "test home") == 1,
                    "A saved home must teleport successfully");
            helper.assertTrue(
                    player.blockPosition().distSqr(homePosition) <= 1.0D,
                    "A saved home must restore its dimension and position");
        }
        helper.succeed();
    }

    public static void homeSharingIsOwnerAndHomeScoped(GameTestHelper helper) {
        try (GameTestPlayer ownerPlayer = GameTestPlayer.mock(helper);
                GameTestPlayer guestPlayer = GameTestPlayer.mock(helper)) {
            ServerPlayer owner = ownerPlayer.player();
            ServerPlayer guest = guestPlayer.player();
            HomeData data = HomeData.get(helper.getLevel().getServer());
            BlockPos homePosition = helper.absolutePos(new BlockPos(2, 2, 2));
            HomeLocation home =
                    new HomeLocation(
                            helper.getLevel().dimension().identifier(),
                            Vec3.atBottomCenterOf(homePosition),
                            0.0F,
                            0.0F);
            data.setHome(owner.getUUID(), "base", home, 10);
            data.setHome(owner.getUUID(), "mine", home, 10);
            helper.assertTrue(
                    data.grant(owner.getUUID(), "base", List.of(guest.getUUID())) == 1,
                    "Sharing a home must grant one owner-scoped entry");
            helper.assertTrue(
                    data.allows(owner.getUUID(), "base", guest.getUUID()),
                    "The shared home must be accessible to the guest");
            helper.assertTrue(
                    !data.allows(owner.getUUID(), "mine", guest.getUUID()),
                    "Sharing one home must not expose the owner's other homes");
            helper.assertTrue(
                    HomeTeleportService.teleport(guest, home, "shared test home") == 1,
                    "An allowed guest must be able to use the shared location");
            helper.assertTrue(
                    guest.blockPosition().distSqr(homePosition) <= 1.0D,
                    "The guest must reach the shared home");
        }
        helper.succeed();
    }

    public static void commandsNormalizeNamesAndEnforceLimits(GameTestHelper helper) {
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            HomeData data = HomeData.get(helper.getLevel().getServer());
            BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
            player.teleportTo(target.getX() + 0.25D, target.getY(), target.getZ() + 0.75D);
            player.setYRot(70.0F);
            player.setXRot(-15.0F);
            helper.assertTrue(
                    execute(helper, player, "sethome BaSe_1") == 1,
                    "A valid mixed-case home must be created");
            HomeLocation home = data.getHome(player.getUUID(), "base_1").orElseThrow();
            helper.assertTrue(
                    home.position()
                                    .left()
                                    .equals(
                                            Optional.of(
                                                    new Vec3(
                                                            target.getX() + 0.25D,
                                                            target.getY(),
                                                            target.getZ() + 0.75D)))
                            && home.yaw() == 70.0F
                            && home.pitch() == -15.0F,
                    "The command must save the exact position and rotation");
            helper.assertTrue(
                    execute(helper, player, "sethome bad.name") == 0,
                    "Invalid characters must be rejected");
            helper.assertTrue(
                    execute(helper, player, "sethome " + "a".repeat(33)) == 0,
                    "Names beyond 32 characters must be rejected");
            for (int i = 1; i < SimpleHomesConfig.maximumHomes(); i++) {
                execute(helper, player, "sethome home_" + i);
            }
            helper.assertTrue(
                    execute(helper, player, "sethome overflow") == 0,
                    "The command must enforce the configured limit");
            helper.assertTrue(
                    execute(helper, player, "sethome BASE_1") == 1,
                    "Updating at the limit must remain possible");
            player.teleportTo(target.getX() + 15.0D, target.getY(), target.getZ() + 15.0D);
            helper.assertTrue(
                    execute(helper, player, "home BASE_1") == 1,
                    "Home lookup must be case-insensitive");
            helper.assertTrue(
                    player.getX() == target.getX() + 0.25D
                            && player.getY() == target.getY()
                            && player.getZ() == target.getZ() + 0.75D,
                    "The home command must restore the saved position");
            helper.assertTrue(
                    execute(helper, player, "home missing") == 0, "A missing home must fail");
        }
        helper.succeed();
    }

    public static void sharingCommandsEnforceAndRevokeAccess(GameTestHelper helper) {
        try (GameTestPlayer ownerPlayer = GameTestPlayer.mock(helper);
                GameTestPlayer guestPlayer = GameTestPlayer.mock(helper)) {
            ServerPlayer owner = ownerPlayer.player();
            ServerPlayer guest = guestPlayer.player();
            String tag = "simplehomes_" + owner.getUUID();
            owner.addTag(tag);
            String guestTag = "simplehomes_" + guest.getUUID();
            guest.addTag(guestTag);
            String guestSelector = "@a[tag=" + guestTag + "]";
            ownerPlayer.moveTo(helper, new BlockPos(2, 1, 2));
            execute(helper, owner, "sethome base");
            execute(helper, owner, "sethome mine");
            String sharedHome = "homeof @a[tag=" + tag + "] base";
            helper.assertTrue(
                    execute(helper, guest, sharedHome) == 0, "An uninvited guest must be denied");
            helper.assertTrue(
                    execute(helper, owner, "sharehome base " + guestSelector) == 1,
                    "Sharing must grant access");
            helper.assertTrue(
                    execute(helper, owner, "sharehome base " + guestSelector) == 0,
                    "Duplicate sharing must not count twice");
            helper.assertTrue(
                    execute(helper, guest, sharedHome) == 1, "A shared home must be usable");
            helper.assertTrue(
                    execute(helper, guest, "homeof @a[tag=" + tag + "] mine") == 0,
                    "Other homes must stay private");
            helper.assertTrue(
                    execute(helper, guest, "sharehome base @a[tag=" + tag + "]") == 0,
                    "Guests cannot share another owner's home");
            helper.assertTrue(
                    execute(helper, owner, "sharehome revoke base " + guestSelector) == 1,
                    "Revocation must remove access");
            helper.assertTrue(
                    execute(helper, guest, sharedHome) == 0, "Revoked guests must be denied");
            helper.assertTrue(
                    execute(helper, owner, "homeof @a[tag=" + tag + "] base") == 1,
                    "Owners must retain access");
            helper.assertTrue(
                    execute(helper, owner, "homeof @a[tag=" + tag + "] missing") == 0,
                    "An owner's missing home must fail");
            helper.assertTrue(
                    execute(helper, guest, "homeof @a base") == 0,
                    "A selector that matches several players must be refused");
            helper.succeed();
        }
    }

    public static void teleportsAcrossDimensionsAndRejectsMissingDimensions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The test server must provide the Nether");
        // The home's chunk is loaded here, so the teleport is immediate; a home whose chunk nobody
        // has loaded is covered by the unloaded-chunk tests.
        nether.getChunk(0, -1);
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            HomeLocation home =
                    new HomeLocation(
                            Level.NETHER.identifier(),
                            new Vec3(8.5D, 80.0D, -11.5D),
                            65.0F,
                            -25.0F);
            helper.assertTrue(
                    HomeTeleportService.teleport(player, home, "Nether home") == 1,
                    "Cross-dimension teleport must succeed");
            helper.assertTrue(
                    player.level() == nether, "Teleport must restore the saved dimension");
            helper.assertTrue(
                    player.getX() == 8.5D && player.getY() == 80.0D && player.getZ() == -11.5D,
                    "Teleport must restore the position");
            helper.assertTrue(
                    player.getYRot() == 65.0F && player.getXRot() == -25.0F,
                    "Teleport must restore rotation");
            var before = player.position();
            HomeLocation missing =
                    new HomeLocation(
                            Identifier.fromNamespaceAndPath("simplehomes", "missing"),
                            Vec3.ZERO,
                            0.0F,
                            0.0F);
            helper.assertTrue(
                    HomeTeleportService.teleport(player, missing, "missing home") == 0,
                    "Unavailable dimensions must fail");
            helper.assertTrue(
                    player.level() == nether && player.position().equals(before),
                    "A failed teleport must not move the player");
            helper.succeed();
        }
    }

    public static void messagesUseModFeedbackAndHelpIsRegistered(GameTestHelper helper) {
        try (var owner = GameTestPlayer.join(helper, "HomesOwner").gameMode(GameType.SPECTATOR);
                var guest =
                        GameTestPlayer.join(helper, "HomesGuest").gameMode(GameType.SPECTATOR)) {
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, owner.player(), "sethome styled") == 1,
                    "Creating a home must still return 1");
            assertMessage(helper, owner, "[SimpleHomes] Created home 'styled'.", 0x55FF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, owner.player(), "home missing") == 0,
                    "A missing home must still return 0");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] You do not have a home named 'missing'.",
                    0xFF5555);
            owner.clearMessages();
            String guestSelector = "@a[name=" + guest.player().getScoreboardName() + "]";
            helper.assertTrue(
                    execute(helper, owner.player(), "sharehome styled " + guestSelector) == 1,
                    "Sharing must return the number of players granted");
            assertMessage(
                    helper, owner, "[SimpleHomes] Granted home access for 1 player(s).", 0x55FF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, owner.player(), "sharehome styled " + guestSelector) == 0,
                    "Repeated sharing must return 0");
            assertMessage(
                    helper, owner, "[SimpleHomes] Granted home access for 0 player(s).", 0xFFFF55);
            owner.clearMessages();
            owner.command("simplehomes help");
            helper.assertTrue(
                    owner.received(
                            "[SimpleHomes] /sethome <name> - create or update a home where you stand"),
                    "/simplehomes help must list the commands, got " + owner.messageTexts());
        }
        helper.assertTrue(
                ServerModHelpRegistry.entries().stream()
                        .anyMatch(
                                entry ->
                                        entry.modId().equals(SimpleHomes.MOD_ID)
                                                && entry.helpCommand().equals("/simplehomes help")
                                                && entry.adminConfigCommand() == null),
                "SimpleHomes must be registered in /servermods");
        var output = new CapturedCommandOutput();
        helper.getLevel()
                .getServer()
                .getCommands()
                .performPrefixedCommand(output.source(helper.getLevel().getServer()), "servermods");
        helper.assertTrue(
                output.text()
                        .contains(
                                "SimpleHomes: Adds named, persistent homes that you can share with other players. /simplehomes help"),
                "/servermods must list SimpleHomes, got " + output.text());
        helper.succeed();
    }

    private static void assertMessage(
            GameTestHelper helper, GameTestPlayer player, String text, int bodyColor) {
        List<Component> messages = player.messages();
        helper.assertTrue(
                messages.size() == 1 && messages.getFirst().getString().equals(text),
                "Expected only \"" + text + "\", got " + player.messageTexts());
        List<Integer> colors = new ArrayList<>();
        messages.getFirst()
                .visit(
                        (style, segment) -> {
                            if (!segment.isEmpty()) colors.add(style.getColor().getValue());
                            return Optional.empty();
                        },
                        Style.EMPTY);
        helper.assertTrue(
                colors.equals(List.of(0xAAAAAA, bodyColor)),
                "Expected a grey prefix and a coloured body for \"" + text + "\", got " + colors);
    }

    public static void rejectedTeleportDoesNotReportSuccess(GameTestHelper helper) {
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            var before = player.position();
            player.discard();
            HomeLocation home =
                    new HomeLocation(
                            helper.getLevel().dimension().identifier(),
                            Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))),
                            0.0F,
                            0.0F);
            helper.assertTrue(
                    HomeTeleportService.teleport(player, home, "test home") == 0,
                    "A teleport rejected by Minecraft must not return success");
            helper.assertTrue(
                    player.position().equals(before),
                    "A rejected teleport must not move the player");
        }
        helper.succeed();
    }

    public static void homesLandWhereThePlayerStood(GameTestHelper helper) {
        List<Block> blocks = List.of(Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.SOUL_SAND);
        List<Double> heights = List.of(15.0D / 16.0D, 15.0D / 16.0D, 14.0D / 16.0D);
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos block = new BlockPos(1 + 2 * i, 1, 1);
                helper.setBlock(block, blocks.get(i));
                BlockPos absolute = helper.absolutePos(block);
                Vec3 standing =
                        new Vec3(
                                absolute.getX() + 0.9D,
                                absolute.getY() + heights.get(i),
                                absolute.getZ() + 0.2D);
                player.teleportTo(standing.x(), standing.y(), standing.z());
                String name = "short_" + i;
                helper.assertTrue(
                        execute(helper, player, "sethome " + name) == 1,
                        "/sethome must work on " + blocks.get(i));
                player.teleportTo(standing.x() + 20.0D, standing.y(), standing.z() + 20.0D);
                helper.assertTrue(
                        execute(helper, player, "home " + name) == 1,
                        "/home must work on " + blocks.get(i));
                helper.assertTrue(
                        player.position().equals(standing),
                        "/home must land where /sethome stood on "
                                + blocks.get(i)
                                + ": expected "
                                + standing
                                + ", landed on "
                                + player.position());
            }
        }
        helper.succeed();
    }

    /** A home in water is survivable, so the lava and void refusals leave it usable. */
    public static void aHomeInWaterStillTeleports(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer owner =
                GameTestPlayer.join(helper, "HomesInWater").gameMode(GameType.SURVIVAL)) {
            ServerPlayer player = owner.player();
            owner.moveTo(helper, new BlockPos(1, 1, 1));
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.WATER);
            Vec3 inWater = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2)));
            HomeData.get(level.getServer())
                    .setHome(
                            player.getUUID(),
                            "water",
                            new HomeLocation(level.dimension().identifier(), inWater, 0.0F, 0.0F),
                            SimpleHomesConfig.maximumHomes());
            helper.assertTrue(
                    execute(helper, player, "home water") == 1,
                    "A home in water must teleport, got " + owner.messageTexts());
            helper.assertTrue(
                    player.position().equals(inWater),
                    "A home in water must move the player there, got " + player.position());
        }
        helper.succeed();
    }

    public static void obstructedHomesFailWithoutMovingThePlayer(GameTestHelper helper) {
        try (GameTestPlayer owner = GameTestPlayer.join(helper, "HomesBuiltOver")) {
            ServerPlayer player = owner.player();
            BlockPos home = new BlockPos(2, 1, 2);
            owner.moveTo(helper, home);
            helper.assertTrue(
                    execute(helper, player, "sethome base") == 1, "/sethome must create the home");
            BlockPos gap = new BlockPos(4, 1, 2);
            owner.moveTo(helper, gap);
            helper.assertTrue(
                    execute(helper, player, "sethome gap") == 1, "/sethome must create the home");
            helper.setBlock(home, Blocks.STONE);
            helper.setBlock(home.above(), Blocks.STONE);
            helper.setBlock(gap.above(), Blocks.STONE);
            owner.moveTo(helper, new BlockPos(6, 1, 6));
            Vec3 before = player.position();
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home base") == 0, "An obstructed home must fail");
            helper.assertTrue(
                    player.position().equals(before),
                    "An obstructed home must not move the player");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] Could not teleport to home 'base' because it is obstructed.",
                    0xFF5555);
            helper.assertTrue(
                    execute(helper, player, "home gap") == 1,
                    "A one-block gap must still work, since the player can crawl there");
        }
        helper.succeed();
    }

    public static void vanillaTpLeavesTheDestinationChunkUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer vanilla = GameTestPlayer.join(helper, "HomesVanilla")) {
            ServerPlayer moved = vanilla.player();
            vanilla.moveTo(helper, new BlockPos(4, 1, 4));
            BlockPos other = helper.absolutePos(new BlockPos(4, 1, 4)).offset(100_000, 0, 20_000);
            int otherChunkX = SectionPos.blockToSectionCoord(other.getX());
            int otherChunkZ = SectionPos.blockToSectionCoord(other.getZ());
            helper.assertTrue(
                    !level.hasChunk(otherChunkX, otherChunkZ),
                    "The /tp destination's chunk must not be loaded before /tp");
            Vec3 teleported = Vec3.atBottomCenterOf(other);
            // Vec3Argument does not take the exponent notation Double.toString uses past 10^7.
            execute(
                    helper,
                    moved,
                    String.format(
                            Locale.ROOT,
                            "tp %s %.1f %.1f %.1f",
                            moved.getScoreboardName(),
                            teleported.x(),
                            teleported.y(),
                            teleported.z()));
            helper.assertTrue(
                    moved.position().equals(teleported),
                    "Vanilla /tp must move the player to the unloaded chunk: expected "
                            + teleported
                            + ", landed on "
                            + moved.position());
            helper.assertTrue(
                    !level.hasChunk(otherChunkX, otherChunkZ),
                    "Vanilla /tp must not load the destination chunk either: it moves the player"
                            + " and lets the player's own chunk ticket load the chunk");
        }
        helper.succeed();
    }

    // The GameTest server runs its ticks as fast as it can, measured between 20 and 7000 ticks a
    // second on the machines that ran these tests on 14 Nov 2026, so a tick budget cannot bound a
    // wait for a chunk load: the load needs wall time, and the test itself checks that instead.
    // Two minutes, because one far chunk took longer than 30 s to load while the two other tests
    // waiting on far chunks in the same batch finished.
    private static final long LOAD_TIMEOUT_NANOS = 120_000_000_000L;

    private static void assertStillLoading(GameTestHelper helper, long started) {
        if (System.nanoTime() - started > LOAD_TIMEOUT_NANOS) {
            helper.fail("The chunk did not load or unload within 2 min of wall time");
        }
    }

    public static void homesInUnloadedChunksTeleportAfterLoadingThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final assertion, which runs after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesFaraway");
        ServerPlayer player = owner.player();
        owner.moveTo(helper, new BlockPos(2, 1, 2));
        BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
        // The GameTest world is flat (WorldPresets.FLAT_ALL_DIMENSIONS, GameTestServer.java:122),
        // so the surface of a chunk far away is at the surface height here.
        BlockPos far =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ());
        int farChunkX = SectionPos.blockToSectionCoord(far.getX());
        int farChunkZ = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(
                !level.hasChunk(farChunkX, farChunkZ),
                "The home's chunk must not be loaded before /home");
        Vec3 expected = Vec3.atBottomCenterOf(far);
        HomeData.get(level.getServer())
                .setHome(
                        player.getUUID(),
                        "far",
                        new HomeLocation(level.dimension().identifier(), expected, 0.0F, 0.0F),
                        SimpleHomesConfig.maximumHomes());
        helper.assertTrue(
                execute(helper, player, "home far") == 1,
                "A home in a chunk nobody loaded must be accepted: its chunk is loaded first, as"
                        + " vanilla's spawn search does");
        // The chunk ticket is given back once the teleport attempt is done, and a joined test
        // player never adds the ticket of its own position (its connection does not tick), so the
        // chunk being loaded again is not what this waits for: the arrival is.
        long started = System.nanoTime();
        helper.succeedWhen(
                () -> {
                    assertStillLoading(helper, started);
                    helper.assertTrue(
                            player.position().equals(expected),
                            "The player must arrive at the home: expected "
                                    + expected
                                    + ", landed on "
                                    + player.position());
                    owner.close();
                });
    }

    public static void aHomeInsideTheGroundOfAnUnloadedChunkStillFails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final assertion, which runs after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesBuriedFaraway");
        ServerPlayer player = owner.player();
        owner.moveTo(helper, new BlockPos(2, 1, 2));
        BlockPos buried =
                helper.absolutePos(new BlockPos(2, 1, 2))
                        .offset(100_000, 0, 192_000)
                        .atY(level.getMinY());
        int chunkX = SectionPos.blockToSectionCoord(buried.getX());
        int chunkZ = SectionPos.blockToSectionCoord(buried.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The buried home's chunk must not be loaded before /home");
        HomeData.get(level.getServer())
                .setHome(
                        player.getUUID(),
                        "buried",
                        new HomeLocation(
                                level.dimension().identifier(),
                                Vec3.atBottomCenterOf(buried),
                                0.0F,
                                0.0F),
                        SimpleHomesConfig.maximumHomes());
        Vec3 before = player.position();
        owner.clearMessages();
        helper.assertTrue(
                execute(helper, player, "home buried") == 1,
                "A home in a chunk nobody loaded is accepted while its chunk loads");
        long started = System.nanoTime();
        helper.succeedWhen(
                () -> {
                    assertStillLoading(helper, started);
                    helper.assertTrue(
                            owner.received(
                                    "[SimpleHomes] Could not teleport to home 'buried' because it"
                                            + " is obstructed."),
                            "A home inside the ground of a chunk nobody loaded must fail once the"
                                    + " chunk is loaded, got "
                                    + owner.messageTexts());
                    helper.assertTrue(
                            player.position().equals(before),
                            "A home in an unloaded chunk must not move the player");
                    owner.close();
                });
    }

    public static void aSecondHomeRequestWhileTheChunkLoadsWaitsForTheLoadToo(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final assertion, which runs after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesTwice");
        ServerPlayer player = owner.player();
        owner.moveTo(helper, new BlockPos(2, 1, 2));
        BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos far =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ() + 48_000);
        int chunkX = SectionPos.blockToSectionCoord(far.getX());
        int chunkZ = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The home's chunk must not be loaded before /home");
        Vec3 expected = Vec3.atBottomCenterOf(far);
        HomeData.get(level.getServer())
                .setHome(
                        player.getUUID(),
                        "far",
                        new HomeLocation(level.dimension().identifier(), expected, 0.0F, 0.0F),
                        SimpleHomesConfig.maximumHomes());
        Vec3 before = player.position();
        owner.clearMessages();
        helper.assertTrue(
                execute(helper, player, "home far") == 1,
                "A home in a chunk nobody loaded must be accepted while its chunk loads");
        helper.assertTrue(
                execute(helper, player, "home far") == 1,
                "A repeated /home while the chunk loads must be accepted too");
        helper.assertTrue(
                level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "A repeated /home while the chunk loads must wait for the load as well, not load"
                        + " the chunk on the server thread");
        helper.assertTrue(
                player.position().equals(before),
                "Neither /home may move the player before the chunk has loaded");
        helper.assertTrue(
                owner.messageTexts()
                        .equals(
                                List.of(
                                        "[SimpleHomes] Loading home 'far'...",
                                        "[SimpleHomes] Loading home 'far'...")),
                "Each /home must say once that the home is loading, got " + owner.messageTexts());
        long started = System.nanoTime();
        helper.succeedWhen(
                () -> {
                    assertStillLoading(helper, started);
                    helper.assertTrue(
                            player.position().equals(expected),
                            "The player must arrive at the home: expected "
                                    + expected
                                    + ", landed on "
                                    + player.position());
                    helper.assertTrue(
                            owner.received("[SimpleHomes] Teleported to home 'far'."),
                            "The arrival must be reported, got " + owner.messageTexts());
                    owner.close();
                });
    }

    public static void aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
        ChunkPos far =
                new ChunkPos(
                        SectionPos.blockToSectionCoord(here.getX() + 100_000),
                        SectionPos.blockToSectionCoord(here.getZ() + 72_000));
        helper.assertTrue(
                level.getChunkSource().getChunkNow(far.x(), far.z()) == null,
                "The chunk must not be loaded before the test");
        // Two teleports wait on the same chunk, and the second one ends first, as one does whose
        // player left or whose other chunks were already there.
        CompletableFuture<?> first = HomeTeleportService.load(level, List.of(far));
        HomeTeleportService.load(level, List.of(far));
        HomeTeleportService.release(level, List.of(far));
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    first.isDone(), "The first teleport's load must finish");
                        })
                // Room for the distance manager to drop the chunk if its ticket had gone.
                .thenIdle(5)
                .thenExecute(
                        () -> {
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(far.x(), far.z()) != null,
                                    "The chunk must stay loaded while the first teleport still"
                                            + " needs it");
                            HomeTeleportService.release(level, List.of(far));
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(far.x(), far.z()) == null,
                                    "The last teleport to end must give the ticket back");
                        })
                .thenSucceed();
    }

    private static ChunkPos farChunk(GameTestHelper helper, int zOffset) {
        BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
        return new ChunkPos(
                SectionPos.blockToSectionCoord(here.getX() + 100_000),
                SectionPos.blockToSectionCoord(here.getZ() + zOffset));
    }

    private static Vec3 middleOf(ChunkPos chunk) {
        return new Vec3(
                SectionPos.sectionToBlockCoord(chunk.x(), 8) + 0.5D,
                64.0D,
                SectionPos.sectionToBlockCoord(chunk.z(), 8) + 0.5D);
    }

    private static boolean holdsLoadingTicket(ServerLevel level, ChunkPos chunk) {
        TicketStorage tickets = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        return tickets.getTickets(chunk.pack()).stream()
                .anyMatch(ticket -> ticket.getType() == TicketType.PLAYER_LOADING);
    }

    public static void aTicketAnotherModTookAwayIsTakenAgainBeforeArriving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final step, which runs after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesTicketTaken");
        ChunkPos far = farChunk(helper, 216_000);
        helper.assertTrue(
                level.getChunkSource().getChunkNow(far.x(), far.z()) == null,
                "The chunk must not be loaded before the test");
        boolean[] arrived = new boolean[1];
        boolean[] loadedOnArrival = new boolean[1];
        HomeTeleportService.loadThenArrive(
                level,
                owner.player(),
                middleOf(far),
                "the test spot",
                1,
                () -> {
                    arrived[0] = true;
                    loadedOnArrival[0] =
                            level.getChunkSource().getChunkNow(far.x(), far.z()) != null;
                });
        // Another mod's teleport waiting on the same chunk ends: tickets of one type and level are
        // one ticket, so its release removes the ticket this load took as well.
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, far, 0);
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(arrived[0], "The teleport must arrive");
                        })
                .thenExecute(
                        () -> {
                            owner.close();
                            helper.assertTrue(
                                    loadedOnArrival[0],
                                    "The teleport must arrive only once its chunk is loaded again,"
                                            + " not on a chunk that lost its ticket");
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, far),
                                    "The ticket taken again must be given back");
                        })
                .thenSucceed();
    }

    public static void aFailedArrivalIsReportedAndGivesTheTicketBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final step, which runs after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesArrivalFails");
        ChunkPos far = farChunk(helper, 120_000);
        HomeTeleportService.loadThenArrive(
                level,
                owner.player(),
                middleOf(far),
                "the test spot",
                1,
                () -> {
                    throw new IllegalStateException("A GameTest arrival that fails on purpose");
                });
        helper.assertTrue(holdsLoadingTicket(level, far), "The load must take a ticket");
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    owner.received(
                                            "[SimpleHomes] Could not teleport to the test spot."),
                                    "A failed arrival must be reported to the player, got "
                                            + owner.messageTexts());
                        })
                .thenExecute(
                        () -> {
                            owner.close();
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, far),
                                    "A failed arrival must still give the ticket back");
                        })
                .thenSucceed();
    }

    private static boolean isFull(ServerLevel level, ChunkPos chunk) {
        return level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null;
    }

    public static void aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final steps, which run after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesTwoChunks");
        ChunkPos west = farChunk(helper, 240_000);
        ChunkPos east = new ChunkPos(west.x() + 1, west.z());
        // On the edge between the two chunks, so the standing box lies over both.
        Vec3 edge =
                new Vec3(
                        SectionPos.sectionToBlockCoord(east.x()),
                        64.0D,
                        SectionPos.sectionToBlockCoord(west.z(), 8) + 0.5D);
        helper.assertTrue(
                !isFull(level, west) && !isFull(level, east),
                "The chunks must not be loaded before the test");
        boolean[] arrived = new boolean[1];
        boolean[] loadedOnArrival = new boolean[1];
        HomeTeleportService.loadThenArrive(
                level,
                owner.player(),
                edge,
                "the test spot",
                1,
                () -> {
                    arrived[0] = true;
                    loadedOnArrival[0] = isFull(level, west) && isFull(level, east);
                });
        helper.assertTrue(
                holdsLoadingTicket(level, west) && holdsLoadingTicket(level, east),
                "The load must take a ticket on both chunks under the spot");
        // Another mod's teleport waiting on the east chunk only ends and takes the shared ticket.
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, east, 0);
        String refusal =
                "[SimpleHomes] Could not teleport to the test spot because its chunk would not"
                        + " stay loaded.";
        long started = System.nanoTime();
        boolean[] westWithoutTicket = new boolean[1];
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            if (!arrived[0] && !holdsLoadingTicket(level, west)) {
                                westWithoutTicket[0] = true;
                            }
                            helper.assertTrue(
                                    arrived[0] || owner.received(refusal),
                                    "The teleport must arrive or give up");
                        })
                .thenExecute(
                        () -> {
                            List<String> messages = owner.messageTexts();
                            owner.close();
                            helper.assertTrue(
                                    arrived[0],
                                    "Both chunks must be loaded again, not given up on, got "
                                            + messages);
                            helper.assertTrue(
                                    !westWithoutTicket[0],
                                    "The chunk that stayed loaded must keep its ticket while the"
                                            + " teleport waits for the other one");
                            helper.assertTrue(
                                    loadedOnArrival[0],
                                    "The teleport must arrive only once both chunks are loaded");
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, west)
                                            && !holdsLoadingTicket(level, east),
                                    "Both tickets must be given back");
                        })
                .thenSucceed();
    }

    public static void aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesLeavesDuringLoad");
        ChunkPos far = farChunk(helper, 144_000);
        boolean[] arrived = new boolean[1];
        HomeTeleportService.loadThenArrive(
                level, owner.player(), middleOf(far), "the test spot", 1, () -> arrived[0] = true);
        helper.assertTrue(holdsLoadingTicket(level, far), "The load must take a ticket");
        owner.close();
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, far),
                                    "The ticket must be given back when the player has left");
                        })
                .thenExecute(
                        () ->
                                helper.assertTrue(
                                        !arrived[0],
                                        "A player who left during the load must not be teleported"))
                .thenSucceed();
    }

    public static void homesSavedWithOnlyABlockLandOnItsFloor(GameTestHelper helper) {
        List<Block> blocks =
                List.of(
                        Blocks.STONE,
                        Blocks.STONE_SLAB,
                        Blocks.MOSS_CARPET,
                        Blocks.FARMLAND,
                        Blocks.SOUL_SAND,
                        Blocks.DIRT_PATH,
                        Blocks.OAK_FENCE,
                        Blocks.STONE_SLAB,
                        Blocks.AIR);
        // Earlier builds saved the block the feet were in: the one above a full block or a fence,
        // the block itself for the lower ones. The last two have no floor in reach (above a bottom
        // slab, and in mid-air as in creative flight), so they land on the block's bottom centre.
        List<Integer> feetAbove = List.of(1, 0, 0, 0, 0, 0, 1, 1, 3);
        List<Double> floors =
                List.of(
                        0.0D,
                        0.5D,
                        1.0D / 16.0D,
                        15.0D / 16.0D,
                        14.0D / 16.0D,
                        15.0D / 16.0D,
                        0.5D,
                        0.0D,
                        0.0D);
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos block = new BlockPos(1 + 2 * (i % 3), 1, 1 + 2 * (i / 3));
                helper.setBlock(block, blocks.get(i));
                BlockPos feet = helper.absolutePos(block.above(feetAbove.get(i)));
                HomeLocation home =
                        HomeLocation.CODEC
                                .parse(
                                        JsonOps.INSTANCE,
                                        JsonParser.parseString(
                                                "{\"dimension\": \""
                                                        + helper.getLevel().dimension().identifier()
                                                        + "\", \"position\": ["
                                                        + feet.getX()
                                                        + ", "
                                                        + feet.getY()
                                                        + ", "
                                                        + feet.getZ()
                                                        + "]}"))
                                .getOrThrow();
                Vec3 expected = Vec3.upFromBottomCenterOf(feet, floors.get(i));
                player.teleportTo(expected.x() + 20.0D, expected.y(), expected.z() + 20.0D);
                helper.assertTrue(
                        HomeTeleportService.teleport(player, home, "old home") == 1,
                        "An old home on " + blocks.get(i) + " must still work");
                helper.assertTrue(
                        player.position().equals(expected),
                        "An old home must land on top of "
                                + blocks.get(i)
                                + ": expected "
                                + expected
                                + ", landed on "
                                + player.position());
            }
        }
        helper.succeed();
    }

    public static void homesInScaffoldingOrPowderSnowAreJudgedWhereTheyAre(GameTestHelper helper) {
        BlockState scaffolding =
                Blocks.SCAFFOLDING
                        .defaultBlockState()
                        .setValue(ScaffoldingBlock.DISTANCE, 0)
                        .setValue(ScaffoldingBlock.BOTTOM, false);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(2, y, 2), scaffolding);
        }
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.POWDER_SNOW);
        Vec3 climbing = Vec3.upFromBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)), 0.5D);
        Vec3 sunk = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)));
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            for (Vec3 standing : List.of(climbing, sunk)) {
                HomeLocation home =
                        new HomeLocation(
                                helper.getLevel().dimension().identifier(), standing, 0.0F, 0.0F);
                // From above, scaffolding and (in leather boots) powder snow are solid to the
                // player where they stand now, but not at the home.
                player.teleportTo(standing.x() + 20.0D, standing.y() + 10.0D, standing.z());
                helper.assertTrue(
                        HomeTeleportService.teleport(player, home, "test home") == 1,
                        "A home at " + standing + " must work from above it");
                helper.assertTrue(
                        player.position().equals(standing),
                        "The home must land at " + standing + ", landed on " + player.position());
            }
        }
        helper.succeed();
    }

    public static void homeofFindsOwnersWithoutAskingMojang(GameTestHelper helper) {
        HomeData data = HomeData.get(helper.getLevel().getServer());
        try (GameTestPlayer guest = GameTestPlayer.join(helper, "HomesVisitor")) {
            ServerPlayer visitor = guest.player();
            guest.moveTo(helper, new BlockPos(1, 1, 1));
            try (GameTestPlayer owner = GameTestPlayer.join(helper, "HomesAway")) {
                owner.moveTo(helper, new BlockPos(3, 1, 3));
                helper.assertTrue(
                        execute(helper, owner.player(), "sethome base") == 1,
                        "/sethome must create the home");
                helper.assertTrue(
                        execute(helper, owner.player(), "sharehome base HomesVisitor") == 1,
                        "Sharing with an online player by name must work");
            }
            helper.assertTrue(
                    execute(helper, visitor, "homeof HomesAway base") == 1,
                    "A guest must reach the shared home of an owner who is offline");

            UUID unknown = UUIDUtil.createOfflinePlayerUUID("HomesNeverSeen");
            guest.moveTo(helper, new BlockPos(1, 1, 1));
            data.setHome(unknown, "base", HomeLocation.from(visitor), 10);
            data.grant(unknown, "base", List.of(visitor.getUUID()));
            guest.moveTo(helper, new BlockPos(5, 1, 5));
            Vec3 before = visitor.position();
            guest.clearMessages();
            helper.assertTrue(
                    execute(helper, visitor, "homeof HomesNeverSeen base") == 0,
                    "A name the server has not cached must not be looked up");
            helper.assertTrue(
                    visitor.position().equals(before), "An unknown owner must not move the guest");
            assertMessage(
                    helper,
                    guest,
                    "[SimpleHomes] HomesNeverSeen has not shared home 'base' with you.",
                    0xFF5555);

            try (GameTestPlayer hider = GameTestPlayer.join(helper, "HomesHider")) {
                hider.moveTo(helper, new BlockPos(3, 1, 1));
                helper.assertTrue(
                        execute(helper, hider.player(), "sethome base") == 1,
                        "/sethome must create the home");
                guest.clearMessages();
                execute(helper, visitor, "homeof HomesHider base");
                String unshared = guest.messageTexts().getLast();
                guest.clearMessages();
                execute(helper, visitor, "homeof HomesHider vault");
                String missing = guest.messageTexts().getLast();
                helper.assertTrue(
                        missing.equals(unshared.replace("'base'", "'vault'")),
                        "An unshared home and a missing one must give the same answer, got \""
                                + unshared
                                + "\" and \""
                                + missing
                                + "\"");
            }
        }
        helper.succeed();
    }

    public static void ownersRevokeGuestsWhoAreOffline(GameTestHelper helper) {
        GameProfile leaver = new GameProfile(UUID.randomUUID(), "HomesLeaver");
        try (GameTestPlayer owner = GameTestPlayer.join(helper, "HomesKeeper")) {
            owner.moveTo(helper, new BlockPos(2, 1, 2));
            helper.assertTrue(
                    execute(helper, owner.player(), "sethome base") == 1,
                    "/sethome must create the home");
            try (GameTestPlayer guest = GameTestPlayer.join(helper, leaver)) {
                String share = "sharehome base " + guest.player().getScoreboardName();
                helper.assertTrue(
                        execute(helper, owner.player(), share) == 1,
                        "Sharing with an online player by name must work");
            }
            // Nothing is typed after the home name, so the completion has to come from the home
            // branch itself; while "/sharehome revoke <players>" existed, brigadier kept that
            // branch's suggestion provider instead and listed nothing.
            List<String> suggested = suggestions(helper, owner.player(), "sharehome revoke base ");
            helper.assertTrue(
                    suggested.contains("HomesLeaver"),
                    "Revoke must suggest the offline guest before anything is typed, got "
                            + suggested);
            helper.assertTrue(
                    execute(helper, owner.player(), "sharehome revoke base HomesLeaver") == 1,
                    "An owner must be able to revoke a guest who is offline");
            try (GameTestPlayer rejoined = GameTestPlayer.join(helper, leaver)) {
                rejoined.moveTo(helper, new BlockPos(5, 1, 5));
                helper.assertTrue(
                        execute(helper, rejoined.player(), "homeof HomesKeeper base") == 0,
                        "A revoked guest must be denied after rejoining");
            }
        }
        helper.succeed();
    }

    public static void playerOnlyCommandsAnswerNonPlayersThroughModFeedback(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (String command :
                List.of(
                        "sethome base",
                        "home base",
                        "sharehome base @a",
                        "homeof Someone base",
                        "sharehome revoke base Someone")) {
            var output = new CapturedCommandOutput();
            server.getCommands().performPrefixedCommand(output.source(server), command);
            helper.assertTrue(
                    output.text().equals("[SimpleHomes] This command can only be used by players!"),
                    "/"
                            + command
                            + " from the console must get BrainageLib's shared players-only"
                            + " message, got "
                            + output.text());
        }
        helper.succeed();
    }

    public static void commandsClickForThePlayerThroughModFeedback(GameTestHelper helper) {
        try (GameTestPlayer clicked = GameTestPlayer.join(helper, "HomesClicked")) {
            clicked.moveTo(helper, new BlockPos(2, 1, 2));
            clicked.clearMessages();
            clicked.command("sethome clicky");
            Sent sent = drain(clicked);
            helper.assertTrue(
                    sent.messages().equals(List.of("[SimpleHomes] Created home 'clicky'.")),
                    "The command must answer the player, got " + sent.messages());
            helper.assertTrue(
                    sent.sounds().size() == 1,
                    "A player command must send one click sound, got " + sent.sounds().size());
            ClientboundSoundPacket click = sent.sounds().getFirst();
            helper.assertTrue(
                    click.getSound()
                                    .value()
                                    .location()
                                    .equals(SoundEvents.UI_BUTTON_CLICK.value().location())
                            && click.getSource() == SoundSource.UI,
                    "The click must be vanilla's UI button click, got "
                            + click.getSound().value().location()
                            + " on "
                            + click.getSource());
        }
        helper.succeed();
    }

    private record Sent(List<String> messages, List<ClientboundSoundPacket> sounds) {}

    /** Everything the player's client was sent since the last drain, waiting for nothing. */
    private static Sent drain(GameTestPlayer player) {
        List<String> messages = new ArrayList<>();
        List<ClientboundSoundPacket> sounds = new ArrayList<>();
        player.channel().runPendingTasks();
        player.channel().flushOutbound();
        Object outbound;
        while ((outbound = player.channel().readOutbound()) != null) {
            if (outbound
                    instanceof ClientboundSystemChatPacket(Component content, boolean overlay)) {
                if (!overlay) messages.add(content.getString());
            } else if (outbound instanceof ClientboundSoundPacket sound) {
                sounds.add(sound);
            }
            ReferenceCountUtil.release(outbound);
        }
        return new Sent(messages, sounds);
    }

    public static void revokeIsRefusedAsANewHomeName(GameTestHelper helper) {
        try (GameTestPlayer owner = GameTestPlayer.join(helper, "HomesReserved")) {
            ServerPlayer player = owner.player();
            owner.moveTo(helper, new BlockPos(2, 1, 2));
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "sethome revoke") == 0,
                    "A home may not be named 'revoke'");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] The name 'revoke' is reserved by /sharehome revoke. Choose"
                            + " another name.",
                    0xFF5555);
            helper.assertTrue(
                    HomeData.get(helper.getLevel().getServer())
                            .getHome(player.getUUID(), "revoke")
                            .isEmpty(),
                    "Nothing may be stored under the reserved name");
        }
        helper.succeed();
    }

    public static void aHomeSavedUnderTheReservedNameStaysUsable(GameTestHelper helper) {
        try (GameTestPlayer ownerPlayer = GameTestPlayer.join(helper, "HomesOld");
                GameTestPlayer guestPlayer = GameTestPlayer.join(helper, "HomesOldGuest")) {
            ServerPlayer owner = ownerPlayer.player();
            ServerPlayer guest = guestPlayer.player();
            String tag = "simplehomes_" + owner.getUUID();
            owner.addTag(tag);
            ownerPlayer.moveTo(helper, new BlockPos(2, 1, 2));
            // How a home saved by a build that still allowed the name is stored.
            HomeData data = HomeData.get(helper.getLevel().getServer());
            data.setHome(
                    owner.getUUID(),
                    "revoke",
                    HomeLocation.from(owner),
                    SimpleHomesConfig.maximumHomes());
            helper.assertTrue(
                    execute(helper, owner, "home revoke") == 1,
                    "A home saved as 'revoke' must still teleport");
            helper.assertTrue(
                    isUsageError(helper, owner, "sharehome revoke " + guest.getScoreboardName()),
                    "Granting access to a home named 'revoke' has no command any more");
            data.grant(owner.getUUID(), "revoke", List.of(guest.getUUID()));
            helper.assertTrue(
                    execute(helper, guest, "homeof @a[tag=" + tag + "] revoke") == 1,
                    "The guest must reach it");
            helper.assertTrue(
                    execute(helper, owner, "sharehome revoke revoke " + guest.getScoreboardName())
                            == 1,
                    "Its access must still be revocable");
            helper.assertTrue(
                    execute(helper, guest, "homeof @a[tag=" + tag + "] revoke") == 0,
                    "The guest must be denied after the revocation");
            ownerPlayer.clearMessages();
            helper.assertTrue(
                    execute(helper, owner, "sethome revoke") == 0,
                    "The home can no longer be moved to the reserved name");
            helper.assertTrue(
                    data.getHome(owner.getUUID(), "revoke").isPresent(),
                    "The stored home must survive the refused update");
        }
        helper.succeed();
    }

    public static void sharehomeRevokeSuggestsHomeNamesBeforeAnythingIsTyped(
            GameTestHelper helper) {
        try (GameTestPlayer owner = GameTestPlayer.join(helper, "HomesSuggests")) {
            ServerPlayer player = owner.player();
            owner.moveTo(helper, new BlockPos(2, 1, 2));
            helper.assertTrue(
                    execute(helper, player, "sethome base") == 1, "/sethome must create the home");
            helper.assertTrue(
                    execute(helper, player, "sethome mine") == 1, "/sethome must create the home");
            List<String> suggested = suggestions(helper, player, "sharehome revoke ");
            helper.assertTrue(
                    suggested.equals(List.of("base", "mine")),
                    "/sharehome revoke must suggest the home names before anything is typed, got "
                            + suggested);
        }
        helper.succeed();
    }

    public static void anOldBlockOnlyHomeInAnUnloadedChunkLandsOnItsBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final step, which runs after this method has returned.
        GameTestPlayer owner = GameTestPlayer.join(helper, "HomesOldFar");
        ServerPlayer player = owner.player();
        owner.moveTo(helper, new BlockPos(2, 1, 2));
        BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos far =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ() + 24_000);
        int chunkX = SectionPos.blockToSectionCoord(far.getX());
        int chunkZ = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The home's chunk must not be loaded before /home");
        // A bottom slab in the home block: the floor rule lands on it, half a block up, where the
        // block's bottom centre that needs no world is inside it. Placing it loads the chunk, so
        // the steps below wait for the chunk to unload before /home.
        level.setBlock(far, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
        // A home saved by a build that stored only the block, as
        // homesSavedWithOnlyABlockLandOnItsFloor builds them.
        HomeData.get(level.getServer())
                .setHome(
                        player.getUUID(),
                        "old",
                        HomeLocation.CODEC
                                .parse(
                                        JsonOps.INSTANCE,
                                        JsonParser.parseString(
                                                "{\"dimension\": \""
                                                        + level.dimension().identifier()
                                                        + "\", \"position\": ["
                                                        + far.getX()
                                                        + ", "
                                                        + far.getY()
                                                        + ", "
                                                        + far.getZ()
                                                        + "]}"))
                                .getOrThrow(),
                        SimpleHomesConfig.maximumHomes());
        Vec3 expected = Vec3.upFromBottomCenterOf(far, 0.5D);
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                                    "The home's chunk must unload before /home");
                        })
                .thenExecute(
                        () ->
                                helper.assertTrue(
                                        execute(helper, player, "home old") == 1,
                                        "An old home in a chunk nobody loaded must be accepted"
                                                + " while its chunk loads"))
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    player.position().equals(expected),
                                    "It must land on the slab in its block, the floor rule"
                                            + " reading the loaded chunk: expected "
                                            + expected
                                            + ", landed on "
                                            + player.position()
                                            + ", messages "
                                            + owner.messageTexts());
                        })
                .thenExecute(owner::close)
                .thenSucceed();
    }

    public static void homesInLavaOrOverTheVoidFailWithoutMovingThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer owner =
                GameTestPlayer.join(helper, "HomesDangerous").gameMode(GameType.SURVIVAL)) {
            ServerPlayer player = owner.player();
            owner.moveTo(helper, new BlockPos(2, 1, 2));
            HomeData data = HomeData.get(level.getServer());
            BlockPos lava = helper.absolutePos(new BlockPos(4, 1, 2));
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.LAVA);
            data.setHome(
                    player.getUUID(),
                    "lava",
                    new HomeLocation(
                            level.dimension().identifier(),
                            Vec3.atBottomCenterOf(lava),
                            0.0F,
                            0.0F),
                    SimpleHomesConfig.maximumHomes());
            Vec3 before = player.position();
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home lava") == 0, "A home in lava must be refused");
            helper.assertTrue(
                    player.position().equals(before), "A home in lava must not move the player");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] Could not teleport to home 'lava' because it is in lava.",
                    0xFF5555);

            BlockPos overVoid = helper.absolutePos(new BlockPos(2, 1, 2)).offset(2_000, 0, 2_000);
            level.getChunk(
                    SectionPos.blockToSectionCoord(overVoid.getX()),
                    SectionPos.blockToSectionCoord(overVoid.getZ()));
            for (int y = level.getMinY(); y <= overVoid.getY(); y++) {
                level.setBlockAndUpdate(
                        new BlockPos(overVoid.getX(), y, overVoid.getZ()),
                        Blocks.AIR.defaultBlockState());
            }
            data.setHome(
                    player.getUUID(),
                    "void",
                    new HomeLocation(
                            level.dimension().identifier(),
                            Vec3.atBottomCenterOf(overVoid),
                            0.0F,
                            0.0F),
                    SimpleHomesConfig.maximumHomes());
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home void") == 0,
                    "A home over the void must be refused");
            helper.assertTrue(
                    player.position().equals(before),
                    "A home over the void must not move the player");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] Could not teleport to home 'void' because it is over the void.",
                    0xFF5555);
        }
        helper.succeed();
    }

    /**
     * A home next to a lava block, with no lava under the centre of its feet, fails once the
     * player's box would reach into the lava, 0.25 blocks from it and even by 0.0005 blocks, and
     * still works 0.35 blocks from it, where the box (0.6 wide) stays clear. Lava at a standing
     * player's head height also fails a player who crawls now and could stand up there.
     */
    public static void aHomeBesideLavaFailsWhenThePlayerWouldTouchIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer owner =
                GameTestPlayer.join(helper, "HomesBesideLava").gameMode(GameType.SURVIVAL)) {
            ServerPlayer player = owner.player();
            owner.moveTo(helper, new BlockPos(1, 1, 1));
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.LAVA);
            Vec3 beside = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 2)));
            HomeData data = HomeData.get(level.getServer());
            data.setHome(
                    player.getUUID(),
                    "edge",
                    new HomeLocation(
                            level.dimension().identifier(), beside.add(0.25, 0.0, 0.0), 0.0F, 0.0F),
                    SimpleHomesConfig.maximumHomes());
            data.setHome(
                    player.getUUID(),
                    "near",
                    new HomeLocation(
                            level.dimension().identifier(), beside.add(0.15, 0.0, 0.0), 0.0F, 0.0F),
                    SimpleHomesConfig.maximumHomes());
            // Lava hurts a box that overlaps it by more than 1e-7 (Entity.java:1346, 1376,
            // 1426-1428; AABB.java:401-409), well short of 0.0005 blocks.
            data.setHome(
                    player.getUUID(),
                    "graze",
                    new HomeLocation(
                            level.dimension().identifier(),
                            beside.add(0.2005, 0.0, 0.0),
                            0.0F,
                            0.0F),
                    SimpleHomesConfig.maximumHomes());
            Vec3 before = player.position();
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home edge") == 0,
                    "A home whose box reaches into lava must be refused");
            helper.assertTrue(
                    player.position().equals(before),
                    "A home whose box reaches into lava must not move the player");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] Could not teleport to home 'edge' because it is in lava.",
                    0xFF5555);
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home graze") == 0,
                    "A home whose box reaches 0.0005 blocks into lava must be refused");
            helper.assertTrue(
                    player.position().equals(before),
                    "A home whose box reaches 0.0005 blocks into lava must not move the player");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] Could not teleport to home 'graze' because it is in lava.",
                    0xFF5555);
            helper.assertTrue(
                    execute(helper, player, "home near") == 1,
                    "A home whose box stays clear of the lava must still teleport");
            helper.assertTrue(
                    player.position().equals(beside.add(0.15, 0.0, 0.0)),
                    "A home whose box stays clear of the lava must move the player there, got "
                            + player.position());

            BlockPos crawl = helper.absolutePos(new BlockPos(2, 1, 4));
            helper.setBlock(new BlockPos(2, 2, 4), Blocks.LAVA);
            data.setHome(
                    player.getUUID(),
                    "crawl",
                    new HomeLocation(
                            level.dimension().identifier(),
                            Vec3.atBottomCenterOf(crawl),
                            0.0F,
                            0.0F),
                    SimpleHomesConfig.maximumHomes());
            // A crawling player's box is 0.6 blocks tall (Avatar.java:28), under the lava.
            player.setPose(Pose.SWIMMING);
            Vec3 crawling = player.position();
            owner.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home crawl") == 0,
                    "A home with lava at head height must be refused for a crawling player");
            helper.assertTrue(
                    player.position().equals(crawling),
                    "A home with lava at head height must not move a crawling player");
            assertMessage(
                    helper,
                    owner,
                    "[SimpleHomes] Could not teleport to home 'crawl' because it is in lava.",
                    0xFF5555);
            player.setPose(Pose.STANDING);
        }
        helper.succeed();
    }

    /**
     * Lava cannot hurt a creative or spectator player, or one whose Fire Resistance never ends, so
     * a home in lava still takes them there, while a home over the void, whose damage gets past
     * their protection, is still refused. Fire Resistance that runs out does not count.
     */
    public static void lavaDoesNotStopACreativeOrSpectatorPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer creative =
                        GameTestPlayer.join(helper, "HomesCreative").gameMode(GameType.CREATIVE);
                GameTestPlayer spectator =
                        GameTestPlayer.join(helper, "HomesSpectator").gameMode(GameType.SPECTATOR);
                GameTestPlayer fireproof =
                        GameTestPlayer.join(helper, "HomesFireproof").gameMode(GameType.SURVIVAL)) {
            HomeData data = HomeData.get(level.getServer());
            BlockPos lava = helper.absolutePos(new BlockPos(4, 1, 2));
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.LAVA);
            Vec3 inLava = Vec3.atBottomCenterOf(lava);
            for (GameTestPlayer owner : List.of(creative, spectator)) {
                ServerPlayer player = owner.player();
                owner.moveTo(helper, new BlockPos(1, 1, 1));
                data.setHome(
                        player.getUUID(),
                        "lava",
                        new HomeLocation(level.dimension().identifier(), inLava, 0.0F, 0.0F),
                        SimpleHomesConfig.maximumHomes());
                helper.assertTrue(
                        execute(helper, player, "home lava") == 1,
                        "A home in lava must still take "
                                + player.getScoreboardName()
                                + " there, got "
                                + owner.messageTexts());
                helper.assertTrue(
                        player.position().equals(inLava),
                        "The player must arrive in the lava, got " + player.position());
            }

            ServerPlayer survivor = fireproof.player();
            fireproof.moveTo(helper, new BlockPos(1, 1, 1));
            data.setHome(
                    survivor.getUUID(),
                    "lava",
                    new HomeLocation(level.dimension().identifier(), inLava, 0.0F, 0.0F),
                    SimpleHomesConfig.maximumHomes());
            survivor.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 9600));
            Vec3 outside = survivor.position();
            fireproof.clearMessages();
            helper.assertTrue(
                    execute(helper, survivor, "home lava") == 0,
                    "A home in lava must be refused while Fire Resistance can run out");
            helper.assertTrue(
                    survivor.position().equals(outside),
                    "A home in lava must not move a player whose Fire Resistance can run out");
            assertMessage(
                    helper,
                    fireproof,
                    "[SimpleHomes] Could not teleport to home 'lava' because it is in lava.",
                    0xFF5555);
            survivor.removeEffect(MobEffects.FIRE_RESISTANCE);
            survivor.addEffect(
                    new MobEffectInstance(
                            MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
            fireproof.clearMessages();
            helper.assertTrue(
                    execute(helper, survivor, "home lava") == 1,
                    "A home in lava must take a player whose Fire Resistance never ends there, got "
                            + fireproof.messageTexts());
            helper.assertTrue(
                    survivor.position().equals(inLava),
                    "The fire resistant player must arrive in the lava, got "
                            + survivor.position());

            ServerPlayer player = creative.player();
            creative.moveTo(helper, new BlockPos(1, 1, 1));
            BlockPos overVoid = helper.absolutePos(new BlockPos(2, 1, 2)).offset(3_000, 0, 3_000);
            level.getChunk(
                    SectionPos.blockToSectionCoord(overVoid.getX()),
                    SectionPos.blockToSectionCoord(overVoid.getZ()));
            for (int y = level.getMinY(); y <= overVoid.getY(); y++) {
                level.setBlockAndUpdate(
                        new BlockPos(overVoid.getX(), y, overVoid.getZ()),
                        Blocks.AIR.defaultBlockState());
            }
            data.setHome(
                    player.getUUID(),
                    "void",
                    new HomeLocation(
                            level.dimension().identifier(),
                            Vec3.atBottomCenterOf(overVoid),
                            0.0F,
                            0.0F),
                    SimpleHomesConfig.maximumHomes());
            Vec3 before = player.position();
            creative.clearMessages();
            helper.assertTrue(
                    execute(helper, player, "home void") == 0,
                    "A home over the void must still be refused for a creative player");
            helper.assertTrue(
                    player.position().equals(before),
                    "A home over the void must not move a creative player");
            assertMessage(
                    helper,
                    creative,
                    "[SimpleHomes] Could not teleport to home 'void' because it is over the void.",
                    0xFF5555);
        }
        helper.succeed();
    }

    public static void aHomeInLavaInAnUnloadedChunkFailsOnceTheChunkLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final step, which runs after this method has returned.
        GameTestPlayer owner =
                GameTestPlayer.join(helper, "HomesLavaFar").gameMode(GameType.SURVIVAL);
        ServerPlayer player = owner.player();
        owner.moveTo(helper, new BlockPos(2, 1, 2));
        BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos lava =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ() + 96_000);
        int chunkX = SectionPos.blockToSectionCoord(lava.getX());
        int chunkZ = SectionPos.blockToSectionCoord(lava.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The home's chunk must not be loaded before /home");
        // Placing the lava loads the chunk, so the steps below wait for it to unload before /home.
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        HomeData.get(level.getServer())
                .setHome(
                        player.getUUID(),
                        "lava",
                        new HomeLocation(
                                level.dimension().identifier(),
                                Vec3.atBottomCenterOf(lava),
                                0.0F,
                                0.0F),
                        SimpleHomesConfig.maximumHomes());
        Vec3 before = player.position();
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                                    "The home's chunk must unload before /home");
                        })
                .thenExecute(
                        () -> {
                            owner.clearMessages();
                            helper.assertTrue(
                                    execute(helper, player, "home lava") == 1,
                                    "A home in a chunk nobody loaded must be accepted while its"
                                            + " chunk loads");
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    owner.received(
                                            "[SimpleHomes] Could not teleport to home 'lava'"
                                                    + " because it is in lava."),
                                    "A home in lava in a chunk nobody loaded must fail once the"
                                            + " chunk is loaded, got "
                                            + owner.messageTexts());
                            helper.assertTrue(
                                    player.position().equals(before),
                                    "A home in lava must not move the player");
                        })
                .thenExecute(owner::close)
                .thenSucceed();
    }

    private static List<String> suggestions(
            GameTestHelper helper, ServerPlayer player, String input) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var parse = dispatcher.parse(input, server.createCommandSourceStack().withEntity(player));
        return dispatcher.getCompletionSuggestions(parse).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }

    private static boolean isUsageError(
            GameTestHelper helper, ServerPlayer player, String command) {
        var server = helper.getLevel().getServer();
        try {
            server.getCommands()
                    .getDispatcher()
                    .execute(command, server.createCommandSourceStack().withEntity(player));
            return false;
        } catch (CommandSyntaxException exception) {
            return true;
        }
    }

    private static int execute(GameTestHelper helper, ServerPlayer player, String command) {
        var server = helper.getLevel().getServer();
        try {
            return server.getCommands()
                    .getDispatcher()
                    .execute(command, server.createCommandSourceStack().withEntity(player));
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("Could not execute /" + command, exception);
        }
    }
}
