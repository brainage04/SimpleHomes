package io.github.brainage04.simplehomes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import io.github.brainage04.brainagelib.storage.AtomicFiles;
import io.github.brainage04.brainagelib.storage.JsonFile;
import java.io.IOException;
import java.nio.file.Path;

/** A file that cannot be loaded leaves the default in place and is kept. */
public final class SimpleHomesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_MAXIMUM_HOMES = 10;
    private static int maximumHomes = DEFAULT_MAXIMUM_HOMES;

    private SimpleHomesConfig() {}

    public static int maximumHomes() {
        return maximumHomes;
    }

    public static void load(Path configDirectory) {
        Path configPath = configDirectory.resolve("simplehomes.json");
        maximumHomes = DEFAULT_MAXIMUM_HOMES;
        JsonFile<Integer> file =
                JsonFile.builder(configPath, GSON, Integer.class)
                        .decoder(SimpleHomesConfig::decode)
                        .description(
                                "SimpleHomes configuration (using maximumHomes="
                                        + DEFAULT_MAXIMUM_HOMES
                                        + ")")
                        .logger(SimpleHomes.LOGGER)
                        .build();
        switch (file.load()) {
            case JsonFile.LoadResult.Loaded<Integer>(Integer configured) ->
                    maximumHomes = configured;
            case JsonFile.LoadResult.Missing<Integer> missing -> writeDefault(configPath);
            default -> {}
        }
    }

    private static Integer decode(JsonElement document) {
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
        return configured;
    }

    private static void writeDefault(Path configPath) {
        JsonObject root = new JsonObject();
        root.addProperty("maximumHomes", DEFAULT_MAXIMUM_HOMES);
        try {
            AtomicFiles.writeString(configPath, GSON.toJson(root) + System.lineSeparator());
        } catch (IOException exception) {
            SimpleHomes.LOGGER.error("Could not write the default {}", configPath, exception);
        }
    }
}
