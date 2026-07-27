package io.github.brainage04.simplehomes;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleHomesTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void registersEveryPublicCommand() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		HomeCommandRegistration.register(dispatcher);
		assertNotNull(dispatcher.getRoot().getChild("sethome"));
		assertNotNull(dispatcher.getRoot().getChild("home"));
		assertNotNull(dispatcher.getRoot().getChild("homeof"));
		assertNotNull(dispatcher.getRoot().getChild("sharehome"));
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
	}

	@Test
	void loadsTheConfigurableMaximumAndWritesTheDefault(@TempDir Path directory) throws IOException {
		SimpleHomesConfig.load(directory);
		assertEquals(10, SimpleHomesConfig.maximumHomes());
		assertTrue(Files.readString(directory.resolve("simplehomes.json")).contains("\"maximumHomes\": 10"));
		Files.writeString(directory.resolve("simplehomes.json"), "{\"maximumHomes\":3}");
		SimpleHomesConfig.load(directory);
		assertEquals(3, SimpleHomesConfig.maximumHomes());
	}

	private static HomeLocation location(int coordinate) {
		return new HomeLocation(
				Identifier.withDefaultNamespace("overworld"),
				new BlockPos(coordinate, coordinate, coordinate),
				0.0F,
				0.0F
		);
	}
}
