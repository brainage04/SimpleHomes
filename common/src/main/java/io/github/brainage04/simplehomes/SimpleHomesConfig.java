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
    private static final Settings DEFAULT = new Settings(10);
    private static Settings settings = DEFAULT;

    private SimpleHomesConfig() {}

    public static int maximumHomes() {
        return settings.maximumHomes();
    }

    public static void load(Path configDirectory) {
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
                                        + ")")
                        .logger(SimpleHomes.LOGGER)
                        .build();
        file.loadOrCreate();
        settings = file.value();
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
        return new Settings(configured);
    }

    private record Settings(int maximumHomes) {}
}
