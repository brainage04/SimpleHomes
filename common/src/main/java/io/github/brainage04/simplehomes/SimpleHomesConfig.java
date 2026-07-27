package io.github.brainage04.simplehomes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal loader-neutral JSON configuration. */
public final class SimpleHomesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int DEFAULT_MAXIMUM_HOMES = 10;
	private static int maximumHomes = DEFAULT_MAXIMUM_HOMES;

	private SimpleHomesConfig() {
	}

	public static int maximumHomes() {
		return maximumHomes;
	}

	public static void load(Path configDirectory) {
		Path configPath = configDirectory.resolve("simplehomes.json");
		maximumHomes = DEFAULT_MAXIMUM_HOMES;
		try {
			Files.createDirectories(configDirectory);
			if (Files.notExists(configPath)) {
				writeDefault(configPath);
				return;
			}
			JsonObject root = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
			int configured = root.get("maximumHomes").getAsInt();
			if (configured < 1) {
				throw new IllegalArgumentException("maximumHomes must be at least 1");
			}
			maximumHomes = configured;
		} catch (IOException | RuntimeException exception) {
			SimpleHomes.LOGGER.error("Could not load {}; using maximumHomes={}", configPath, DEFAULT_MAXIMUM_HOMES, exception);
		}
	}

	private static void writeDefault(Path configPath) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("maximumHomes", DEFAULT_MAXIMUM_HOMES);
		Files.writeString(configPath, GSON.toJson(root) + System.lineSeparator());
	}
}
