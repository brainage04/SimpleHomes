package io.github.brainage04.simplehomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.players.NameAndId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NameLookupTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFoundNameReachesTheServerThread() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        NameLookup lookup = lookup(name -> found(profile));
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("steve", answer::set);
        assertEquals(NameLookup.Status.FOUND, answer.get().status());
        assertEquals(profile, answer.get().profile());
    }

    @Test
    void anUnknownNameIsNotFoundAndNothingIsInvented() {
        NameLookup lookup = lookup(name -> notFound());
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("HomesNeverSeen", answer::set);
        assertEquals(NameLookup.Status.NOT_FOUND, answer.get().status());
        assertEquals(null, answer.get().profile());
    }

    @Test
    void aFailingRepositoryIsReportedAsFailed() {
        NameLookup lookup = lookup(name -> failed());
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        assertEquals(NameLookup.Status.FAILED, answer.get().status());
    }

    @Test
    void theServerStopsAskingMojangAfterItsShareOfTheLimit() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        NameLookup lookup = lookup(name -> found(profile));
        for (int use = 0; use < 100; use++) {
            AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
            lookup.lookup("Steve", answer::set);
            assertEquals(NameLookup.Status.FOUND, answer.get().status(), "lookup " + use);
        }
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        assertEquals(NameLookup.Status.TOO_MANY, answer.get().status());
    }

    @Test
    void theAnswerArrivesOnlyWhenTheWorkerAndTheServerThreadRun() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        NameLookup lookup =
                new NameLookup(
                        name -> found(profile),
                        queued::set,
                        runnable -> runnable.run(),
                        () -> true);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        assertEquals(null, answer.get(), "nothing may reach the player before the lookup runs");
        queued.get().run();
        assertEquals(NameLookup.Status.FOUND, answer.get().status());
    }

    /** A word that cannot be a player name is refused without spending a lookup. */
    @Test
    void aNameThatCannotBelongToAnAccountNeverReachesTheRepository() {
        int[] calls = {0};
        NameLookup lookup =
                lookup(
                        name -> {
                            calls[0]++;
                            return notFound();
                        });
        for (String name : List.of("a".repeat(17), "bad name", "na\u00efve")) {
            AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
            lookup.lookup(name, answer::set);
            assertEquals(NameLookup.Status.NOT_FOUND, answer.get().status(), name);
        }
        assertEquals(0, calls[0], "no invalid name may reach the repository");
    }

    /** An answer that arrives after the server has stopped is dropped, not applied. */
    @Test
    void anAnswerIsDroppedWhenTheServerThreadIsGone() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        NameLookup lookup =
                new NameLookup(name -> found(profile), queued::set, Runnable::run, () -> false);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        queued.get().run();
        assertEquals(null, answer.get(), "nothing may be applied off the server thread");
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

    @Test
    void aSecondUseInTheSameSecondIsRefused() {
        long[] now = {0};
        ShareRateLimit limit = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        assertEquals(0, limit.refuse(player));
        assertEquals(1_000_000_000L, limit.refuse(player));
        assertEquals(0, limit.refuse(UUID.randomUUID()), "another player has their own allowance");
        now[0] = 1_000_000_000L;
        assertEquals(0, limit.refuse(player));
    }

    @Test
    void theMinuteLimitCountsTwenty() {
        long[] now = {0};
        ShareRateLimit limit = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        for (int use = 0; use < 20; use++) {
            assertEquals(0, limit.refuse(player), "use " + use);
            now[0] += 1_000_000_000L;
        }
        assertEquals(40_000_000_000L, limit.refuse(player), "the first use leaves the minute");
        // A refused use is not counted, so trying again does not push the wait further out.
        assertEquals(40_000_000_000L, limit.refuse(player));
    }

    @Test
    void theHourLimitCountsFourHundred() {
        long[] now = {0};
        ShareRateLimit limit = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        // Four seconds apart, so the minute limit (20) is never reached before the hour's 400.
        for (int use = 0; use < 400; use++) {
            assertEquals(0, limit.refuse(player), "use " + use);
            now[0] += 4_000_000_000L;
        }
        assertEquals(2_000_000_000_000L, limit.refuse(player), "the first use leaves the hour");
    }

    @Test
    void theWaitIsDescribedInWholeSeconds() {
        assertEquals("1 second", ShareRateLimit.describe(1));
        assertEquals("1 second", ShareRateLimit.describe(1_000_000_000L));
        assertEquals("2 seconds", ShareRateLimit.describe(1_500_000_000L));
    }

    private static NameLookup lookup(NameLookup.Repository repository) {
        Executor direct = Runnable::run;
        return new NameLookup(repository, direct, direct, () -> true);
    }

    private static NameLookup.Answer found(NameAndId profile) {
        return new NameLookup.Answer(NameLookup.Status.FOUND, profile);
    }

    private static NameLookup.Answer notFound() {
        return new NameLookup.Answer(NameLookup.Status.NOT_FOUND, null);
    }

    private static NameLookup.Answer failed() {
        return new NameLookup.Answer(NameLookup.Status.FAILED, null);
    }

    private static HomeLocation location() {
        return new HomeLocation(
                net.minecraft.resources.Identifier.withDefaultNamespace("overworld"),
                new net.minecraft.world.phys.Vec3(1, 2, 3),
                0.0F,
                0.0F);
    }
}
