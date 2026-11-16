package io.github.brainage04.simplehomes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import io.github.brainage04.brainagelib.storage.ConfigFile;
import java.nio.file.Path;

/** A file that cannot be loaded leaves the default in place and is kept. */
public final class SimpleHomesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Settings DEFAULT = new Settings(10, true);
    private static Settings settings = DEFAULT;
    private static Path directory;

    private SimpleHomesConfig() {}

    public static int maximumHomes() {
        return settings.maximumHomes();
    }

    /**
     * Whether a teleport resets the player's fall distance on arrival, as an ender pearl's does
     * (ThrownEnderpearl.java:123,135). Off keeps the fall they had, as vanilla's /tp does
     * (TeleportCommand.java:241-248).
     */
    public static boolean resetFallOnArrival() {
        return settings.resetFallOnArrival();
    }

    public static void load(Path configDirectory) {
        directory = configDirectory;
        ConfigFile<Settings> file =
                ConfigFile.builder(
                                configDirectory.resolve("simplehomes.json"),
                                GSON,
                                Settings.class,
                                DEFAULT)
                        .decoder(SimpleHomesConfig::decode)
                        .description(
                                "SimpleHomes configuration (using maximumHomes="
                                        + DEFAULT.maximumHomes()
                                        + ", resetFallOnArrival="
                                        + DEFAULT.resetFallOnArrival()
                                        + ")")
                        .logger(SimpleHomes.LOGGER)
                        .build();
        file.loadOrCreate();
        settings = file.value();
    }

    /** The directory the config was last loaded from, so a test can put the file back. */
    static Path directory() {
        return directory;
    }

    private static Settings decode(JsonElement document) {
        if (!(document instanceof JsonObject root)
                || !(root.get("maximumHomes") instanceof JsonPrimitive value)) {
            throw new JsonParseException("maximumHomes must be a whole number");
        }
        int configured;
        try {
            configured = value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new JsonParseException(
                    "maximumHomes must be a whole number from 1 to " + Integer.MAX_VALUE,
                    exception);
        }
        if (configured < 1) {
            throw new JsonParseException("maximumHomes must be at least 1");
        }
        // A file written before this setting existed has no field, and keeps the default.
        JsonElement reset = root.get("resetFallOnArrival");
        if (reset == null) {
            return new Settings(configured, DEFAULT.resetFallOnArrival());
        }
        if (!(reset instanceof JsonPrimitive flag) || !flag.isBoolean()) {
            throw new JsonParseException("resetFallOnArrival must be true or false");
        }
        return new Settings(configured, flag.getAsBoolean());
    }

    private record Settings(int maximumHomes, boolean resetFallOnArrival) {}
}
