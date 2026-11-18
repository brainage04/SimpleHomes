package io.github.brainage04.simplehomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NameLookupTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The lookup and its limiter live in BrainageLib, so every mod on the server shares one lookup
     * thread and one share of Mojang's limit. A copy here would quietly take a second share.
     */
    @Test
    void theLookupAndItsLimiterAreNotCopiedHere() {
        for (String name : List.of("NameLookup", "ShareRateLimit")) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("io.github.brainage04.simplehomes." + name),
                    name + " must come from BrainageLib, not from this mod");
        }
    }

    @Test
    void aPlayerKeepsTheNewestNamesAndRepeatsMoveToTheFront() {
        HomeData data = new HomeData();
        UUID guest = UUID.randomUUID();
        data.sawName(guest, "Alex", 1_000);
        data.sawName(guest, "Steve", 2_000);
        assertEquals(
                List.of(
                        new HomeData.SeenName("Steve", 2_000),
                        new HomeData.SeenName("Alex", 1_000)),
                data.names(guest));
        data.sawName(guest, "Alex", 3_000);
        assertEquals(
                List.of(
                        new HomeData.SeenName("Alex", 3_000),
                        new HomeData.SeenName("Steve", 2_000)),
                data.names(guest));
    }

    /** The ten-name cap is what keeps a guest's history bounded, so it needs a step of its own. */
    @Test
    void aGuestKeepsOnlyTheTenNewestNames() {
        HomeData data = new HomeData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 12; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        List<String> kept = data.names(guest).stream().map(HomeData.SeenName::name).toList();
        assertEquals(10, kept.size(), "a guest must keep ten names, got " + kept);
        assertEquals(List.of("Name11", "Name10", "Name9"), kept.subList(0, 3));
        assertTrue(!kept.contains("Name0") && !kept.contains("Name1"), "the oldest must go");
    }

    /**
     * A name an owner typed is recorded without a time, so it can be matched again while "last
     * seen" stays the last join the server recorded.
     */
    @Test
    void aRecordedNameCarriesNoSighting() {
        HomeData data = new HomeData();
        UUID guest = UUID.randomUUID();
        data.sawName(guest, "Steve", 2_000);
        data.recordName(guest, "Alex");
        assertEquals(
                List.of(new HomeData.SeenName("Steve", 2_000), new HomeData.SeenName("Alex", 0)),
                data.names(guest));
        data.recordName(guest, "Alex");
        assertEquals(2, data.names(guest).size(), "a repeated name must not be added twice");
        data.recordName(guest, "Steve");
        assertEquals(
                new HomeData.SeenName("Steve", 2_000),
                data.names(guest).getFirst(),
                "recording a name already seen must keep its time");
    }

    /**
     * A full history of joins drops its oldest join name, so a typed name can still be recorded.
     */
    @Test
    void aFullHistoryOfJoinsMakesRoomForATypedName() {
        HomeData data = new HomeData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 10; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        data.recordName(guest, "Typed");
        List<String> kept = data.names(guest).stream().map(HomeData.SeenName::name).toList();
        assertEquals(10, kept.size(), "the history must stay at ten names, got " + kept);
        assertTrue(kept.contains("Typed"), "the typed name must be recorded, got " + kept);
        assertTrue(!kept.contains("Name0"), "the oldest join name must go, got " + kept);
    }

    /** A full history holding a typed name drops that name before any join name. */
    @Test
    void aFullHistoryDropsItsTypedNameBeforeAJoinName() {
        HomeData data = new HomeData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 9; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        data.recordName(guest, "TypedFirst");
        data.recordName(guest, "TypedSecond");
        List<String> kept = data.names(guest).stream().map(HomeData.SeenName::name).toList();
        assertEquals(10, kept.size(), "the history must stay at ten names, got " + kept);
        assertTrue(
                kept.contains("TypedSecond"), "the new typed name must be recorded, got " + kept);
        assertTrue(!kept.contains("TypedFirst"), "the older typed name must go, got " + kept);
        assertTrue(
                kept.containsAll(
                        java.util.stream.IntStream.range(0, 9)
                                .mapToObj(use -> "Name" + use)
                                .toList()),
                "every join name must survive, got " + kept);
    }

    /**
     * A full history holding two typed names drops the older of them, not the newer: the scan runs
     * from the front, where the oldest typed name sits.
     */
    @Test
    void aFullHistoryDropsTheOldestTypedName() {
        HomeData data = new HomeData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 8; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        data.recordName(guest, "TypedFirst");
        data.recordName(guest, "TypedSecond");
        data.recordName(guest, "TypedThird");
        List<String> kept = data.names(guest).stream().map(HomeData.SeenName::name).toList();
        assertEquals(10, kept.size(), "the history must stay at ten names, got " + kept);
        assertTrue(
                kept.containsAll(List.of("TypedSecond", "TypedThird")),
                "the two newest typed names must survive, got " + kept);
        assertTrue(!kept.contains("TypedFirst"), "the oldest typed name must go, got " + kept);
        assertTrue(
                kept.containsAll(
                        java.util.stream.IntStream.range(0, 8)
                                .mapToObj(use -> "Name" + use)
                                .toList()),
                "every join name must survive, got " + kept);
    }

    /** Recording every join cannot grow the saved data without bound. */
    @Test
    void joinsOutsideTheGuestSetArePruned() {
        HomeData data = new HomeData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        data.setHome(owner, "base", location(), 10);
        data.grant(owner, "base", List.of(guest));
        data.recordJoin(guest, "Guest", 1_000);
        List<UUID> players = new ArrayList<>();
        for (int use = 0; use < 1_100; use++) {
            UUID player = UUID.randomUUID();
            players.add(player);
            data.recordJoin(player, "Player" + use, 2_000L + use);
        }
        data.pruneNames(Set.of(guest));
        assertEquals(
                List.of(new HomeData.SeenName("Guest", 1_000)),
                data.names(guest),
                "a guest must keep their history");
        long kept = players.stream().filter(player -> !data.names(player).isEmpty()).count();
        assertEquals(1_000, kept, "the newest 1000 non-guest histories must stay, got " + kept);
        assertTrue(
                data.names(players.getFirst()).isEmpty(), "the oldest non-guest history must go");
        assertTrue(
                !data.names(players.getLast()).isEmpty(), "the newest non-guest history must stay");
    }

    /** A guest every owner has revoked cannot match a name again, so its history is dropped. */
    @Test
    void aRevokedGuestsHistoryIsDropped() {
        HomeData data = new HomeData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        data.setHome(owner, "base", location(), 10);
        data.grant(owner, "base", List.of(guest));
        data.sawName(guest, "Alex", 1_000);
        data.revoke(owner, "base", List.of(guest));
        assertTrue(data.names(guest).isEmpty(), "a revoked guest must keep no history");
        // A guest another owner still shares with keeps theirs.
        UUID other = UUID.randomUUID();
        data.setHome(other, "spot", location(), 10);
        data.grant(owner, "base", List.of(guest));
        assertEquals(1, data.grant(other, "spot", List.of(guest)), "the other owner must share");
        data.sawName(guest, "Alex", 2_000);
        data.revoke(owner, "base", List.of(guest));
        assertEquals(
                List.of(new HomeData.SeenName("Alex", 2_000)),
                data.names(guest),
                "a guest another owner still shares with must keep their history");
    }

    @Test
    void nameHistorySurvivesSerialization() {
        HomeData data = new HomeData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        data.setHome(owner, "base", location(), 10);
        data.grant(owner, "base", List.of(guest));
        data.sawName(guest, "Alex", 1_000);
        data.sawName(guest, "Steve", 2_000);
        var json = HomeData.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        HomeData restored = HomeData.TYPE.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(data.names(guest), restored.names(guest));
        assertTrue(restored.allows(owner, "base", guest));
    }

    /** A server whose data has no name history still loads; the field is optional. */
    @Test
    void homesSavedBeforeTheHistoryLoadWithoutIt() {
        String owner = "00000000-0000-0000-0000-000000000001";
        String json =
                "{\"homes\":{\""
                        + owner
                        + "\":{\"base\":{\"dimension\":\"minecraft:overworld\",\"position\":[1,2,3]}}}}";
        HomeData data =
                HomeData.TYPE
                        .codec()
                        .parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json))
                        .getOrThrow();
        assertTrue(data.getHome(UUID.fromString(owner), "base").isPresent());
        assertTrue(data.names(UUID.randomUUID()).isEmpty());
    }

    @Test
    void aGuestIsFoundByANameOnlyItsHistoryHolds() {
        HomeData data = new HomeData();
        UUID owner = UUID.randomUUID();
        UUID renamed = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        data.setHome(owner, "base", location(), 10);
        data.grant(owner, "base", List.of(renamed, other));
        data.sawName(renamed, "Alex", 1_000);
        data.sawName(renamed, "Steve", 2_000);
        assertEquals(List.of(renamed), data.guestsNamed(owner, "base", "alex"));
        assertEquals(List.of(renamed), data.guestsNamed(owner, "base", "Steve"));
        assertTrue(data.guestsNamed(owner, "base", "HomesNeverSeen").isEmpty());
    }

    private static HomeLocation location() {
        return new HomeLocation(
                net.minecraft.resources.Identifier.withDefaultNamespace("overworld"),
                new net.minecraft.world.phys.Vec3(1, 2, 3),
                0.0F,
                0.0F);
    }
}
