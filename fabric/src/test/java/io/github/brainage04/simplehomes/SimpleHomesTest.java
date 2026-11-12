package io.github.brainage04.simplehomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimpleHomesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void enforcesTheLimitButAllowsExistingHomesToBeUpdated() {
        HomeData data = new HomeData();
        UUID owner = UUID.randomUUID();
        HomeLocation first = location(1);
        HomeLocation updated = location(2);
        assertEquals(HomeData.SetResult.CREATED, data.setHome(owner, "base", first, 1));
        assertEquals(HomeData.SetResult.LIMIT_REACHED, data.setHome(owner, "mine", updated, 1));
        assertEquals(HomeData.SetResult.UPDATED, data.setHome(owner, "base", updated, 1));
        assertEquals(updated, data.getHome(owner, "base").orElseThrow());
    }

    @Test
    void scopesSharedAccessToAnOwnerAndHome() {
        HomeData data = new HomeData();
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        data.setHome(owner, "base", location(1), 10);
        data.setHome(owner, "mine", location(2), 10);
        assertEquals(1, data.grant(owner, "base", List.of(guest)));
        assertTrue(data.allows(owner, "base", guest));
        assertFalse(data.allows(owner, "mine", guest));
        assertFalse(data.allows(otherOwner, "base", guest));
        assertEquals(1, data.revoke(owner, "base", List.of(guest)));
        assertFalse(data.allows(owner, "base", guest));
        assertEquals(-1, data.revoke(owner, "missing", List.of(guest)));
    }

    @Test
    void loadsTheConfigurableMaximumAndWritesTheDefault(@TempDir Path directory)
            throws IOException {
        try {
            SimpleHomesConfig.load(directory);
            assertEquals(10, SimpleHomesConfig.maximumHomes());
            assertTrue(
                    Files.readString(directory.resolve("simplehomes.json"))
                            .contains("\"maximumHomes\": 10"));
            Files.writeString(directory.resolve("simplehomes.json"), "{\"maximumHomes\":3}");
            SimpleHomesConfig.load(directory);
            assertEquals(3, SimpleHomesConfig.maximumHomes());
        } finally {
            SimpleHomesConfig.load(directory.resolve("reset"));
        }
    }

    @Test
    void configWrittenByThePreviousVersionLoadsAndTheDefaultKeepsItsFormat(@TempDir Path directory)
            throws IOException {
        Path config = directory.resolve("simplehomes.json");
        try {
            SimpleHomesConfig.load(directory);
            assertEquals("{\n  \"maximumHomes\": 10\n}\n", Files.readString(config));
            String previous = "{\n  \"maximumHomes\": 7\n}" + System.lineSeparator();
            Files.writeString(config, previous);
            SimpleHomesConfig.load(directory);
            assertEquals(7, SimpleHomesConfig.maximumHomes());
            assertEquals(previous, Files.readString(config));
        } finally {
            SimpleHomesConfig.load(directory.resolve("reset"));
        }
    }

    @Test
    void invalidConfigurationFallsBackWithoutOverwritingTheFile(@TempDir Path directory)
            throws IOException {
        Path config = directory.resolve("simplehomes.json");
        try {
            for (String json :
                    List.of(
                            "{",
                            "null",
                            "[]",
                            "{}",
                            "{\"maximumHomes\":0}",
                            "{\"maximumHomes\":-1}",
                            "{\"maximumHomes\":\"bad\"}")) {
                Files.writeString(config, "{\"maximumHomes\":3}");
                SimpleHomesConfig.load(directory);
                Files.writeString(config, json);
                SimpleHomesConfig.load(directory);
                assertEquals(10, SimpleHomesConfig.maximumHomes(), json);
                assertEquals(json, Files.readString(config));
            }
        } finally {
            SimpleHomesConfig.load(directory.resolve("reset"));
        }
    }

    @Test
    void fractionalAndOverflowingLimitsUseTheDefault(@TempDir Path directory) throws IOException {
        try {
            for (String number : List.of("1.5", "4294967297", "2147483648", "1e100")) {
                Files.writeString(
                        directory.resolve("simplehomes.json"), "{\"maximumHomes\":" + number + "}");
                SimpleHomesConfig.load(directory);
                assertEquals(10, SimpleHomesConfig.maximumHomes(), number);
            }
        } finally {
            SimpleHomesConfig.load(directory.resolve("reset"));
        }
    }

    @Test
    void homesAndPerHomeSharesSurviveSerialization() {
        HomeData data = new HomeData();
        UUID owner = new UUID(0, 1);
        UUID otherOwner = new UUID(0, 2);
        UUID guest = new UUID(0, 3);
        HomeLocation base =
                new HomeLocation(
                        Identifier.withDefaultNamespace("the_nether"),
                        new Vec3(-12.25D, 64.0D, 32.75D),
                        75.0F,
                        -20.0F);
        data.setHome(owner, "base", base, 10);
        data.setHome(owner, "mine", location(2), 10);
        data.setHome(otherOwner, "base", location(3), 10);
        data.grant(owner, "base", List.of(guest));

        HomeData restored = roundTrip(data);
        assertEquals(base, restored.getHome(owner, "base").orElseThrow());
        assertEquals(location(3), restored.getHome(otherOwner, "base").orElseThrow());
        assertEquals(List.of("base", "mine"), restored.homeNames(owner));
        assertTrue(restored.allows(owner, "base", guest));
        assertFalse(restored.allows(owner, "mine", guest));
        assertFalse(restored.allows(otherOwner, "base", guest));
        assertEquals(HomeData.SetResult.UPDATED, restored.setHome(owner, "base", location(4), 1));
        assertTrue(restored.allows(owner, "base", guest));
        assertEquals(1, restored.revoke(owner, "base", List.of(guest)));
        assertFalse(roundTrip(restored).allows(owner, "base", guest));
    }

    @Test
    void sharingCountsOnlyNewGuestsAndMarksOnlyChangesDirty() {
        HomeData data = new HomeData();
        UUID owner = new UUID(0, 1);
        UUID guest = new UUID(0, 2);
        assertEquals(-1, data.grant(owner, "missing", List.of(guest)));
        data.setHome(owner, "base", location(1), 1);
        data.setDirty(false);
        assertEquals(1, data.grant(owner, "base", List.of(owner, guest, guest)));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertEquals(0, data.grant(owner, "base", List.of(owner, guest)));
        assertFalse(data.isDirty());
        assertEquals(1, data.revoke(owner, "base", List.of(guest, guest)));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertEquals(0, data.revoke(owner, "base", List.of(guest)));
        assertFalse(data.isDirty());
        assertTrue(data.allows(owner, "base", owner));
    }

    @Test
    void aMalformedUuidDropsOnlyItsOwnEntry() {
        String valid = "00000000-0000-0000-0000-000000000001";
        String guest = "00000000-0000-0000-0000-000000000002";
        String home = "{\"dimension\": \"minecraft:overworld\", \"position\": [1, 2, 3]}";
        var json =
                JsonParser.parseString(
                        "{\"homes\": {\""
                                + valid
                                + "\": {\"base\": "
                                + home
                                + "}, \"not-a-uuid\": {\"base\": "
                                + home
                                + "}}, \"shares\": {\""
                                + valid
                                + "\": {\"base\": [\""
                                + guest
                                + "\", \"not-a-uuid\"]}}}");
        var result = HomeData.TYPE.codec().parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        // The server loads the partial result (SavedDataStorage.java:93-95).
        HomeData data = result.resultOrPartial(error -> {}).orElseThrow();
        assertTrue(data.getHome(UUID.fromString(valid), "base").isPresent());
        assertTrue(data.allows(UUID.fromString(valid), "base", UUID.fromString(guest)));
    }

    @Test
    void homesSavedWithOnlyABlockKeepThatBlock() {
        var json =
                JsonParser.parseString(
                        "{\"dimension\": \"minecraft:overworld\", \"position\": [1, 2, 3]}");
        HomeLocation home = HomeLocation.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(Optional.of(new BlockPos(1, 2, 3)), home.position().right());
        var saved =
                HomeLocation.CODEC
                        .encodeStart(JsonOps.INSTANCE, home)
                        .getOrThrow()
                        .getAsJsonObject();
        assertEquals(json.getAsJsonObject().get("position"), saved.get("position"));
        assertFalse(saved.has("pos"));
    }

    private static HomeData roundTrip(HomeData data) {
        var json = HomeData.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        return HomeData.TYPE.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static HomeLocation location(int coordinate) {
        return new HomeLocation(
                Identifier.withDefaultNamespace("overworld"),
                new Vec3(coordinate, coordinate, coordinate),
                0.0F,
                0.0F);
    }
}
