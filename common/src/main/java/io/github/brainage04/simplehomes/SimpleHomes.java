package io.github.brainage04.simplehomes;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class SimpleHomes {
	public static final String MOD_ID = "simplehomes";
	public static final String MOD_NAME = "SimpleHomes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private SimpleHomes() {
	}

	public static void initialize(Path configDirectory) {
		SimpleHomesConfig.load(configDirectory);
		LOGGER.info("{} initialized with a maximum of {} homes per player.", MOD_NAME, SimpleHomesConfig.maximumHomes());
	}

	public static Identifier of(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
