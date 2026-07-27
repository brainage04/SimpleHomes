package io.github.brainage04.simplehomes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class HomeCommandRegistration {
	private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

	private HomeCommandRegistration() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("sethome")
				.then(argument("name", StringArgumentType.word())
						.executes(HomeCommandRegistration::setHome)));
		dispatcher.register(literal("home")
				.then(argument("name", StringArgumentType.word())
						.suggests(HomeCommandRegistration::suggestOwnHomes)
						.executes(HomeCommandRegistration::teleportHome)));
		dispatcher.register(literal("homeof")
				.then(argument("player", EntityArgument.player())
						.then(argument("name", StringArgumentType.word())
								.executes(HomeCommandRegistration::teleportOtherHome))));
		dispatcher.register(literal("sharehome")
				.then(literal("revoke")
						.then(argument("name", StringArgumentType.word())
								.suggests(HomeCommandRegistration::suggestOwnHomes)
								.then(argument("players", EntityArgument.players())
										.executes(context -> changeAccess(context, false)))))
				.then(argument("name", StringArgumentType.word())
						.suggests(HomeCommandRegistration::suggestOwnHomes)
						.then(argument("players", EntityArgument.players())
								.executes(context -> changeAccess(context, true)))));
	}

	private static int setHome(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = normalizedName(context, player);
		if (name == null) return 0;
		HomeData.SetResult result = HomeData.get(context.getSource().getServer()).setHome(
				player.getUUID(),
				name,
				HomeLocation.from(player),
				SimpleHomesConfig.maximumHomes()
		);
		if (result == HomeData.SetResult.LIMIT_REACHED) {
			player.sendSystemMessage(Component.literal(
					"You already have the maximum of " + SimpleHomesConfig.maximumHomes() + " homes."
			));
			return 0;
		}
		String action = result == HomeData.SetResult.CREATED ? "Created" : "Updated";
		player.sendSystemMessage(Component.literal(action + " home '" + name + "'."));
		return 1;
	}

	private static int teleportHome(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = normalizedName(context, player);
		if (name == null) return 0;
		HomeLocation home = HomeData.get(context.getSource().getServer()).getHome(player.getUUID(), name).orElse(null);
		if (home == null) {
			player.sendSystemMessage(Component.literal("You do not have a home named '" + name + "'."));
			return 0;
		}
		return HomeTeleportService.teleport(player, home, "home '" + name + "'");
	}

	private static int teleportOtherHome(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer guest = context.getSource().getPlayerOrException();
		ServerPlayer owner = EntityArgument.getPlayer(context, "player");
		String name = normalizedName(context, guest);
		if (name == null) return 0;
		HomeData data = HomeData.get(context.getSource().getServer());
		HomeLocation home = data.getHome(owner.getUUID(), name).orElse(null);
		if (home == null) {
			guest.sendSystemMessage(Component.literal(owner.getScoreboardName() + " does not have a home named '" + name + "'."));
			return 0;
		}
		if (!data.allows(owner.getUUID(), name, guest.getUUID())) {
			guest.sendSystemMessage(Component.literal(owner.getScoreboardName() + " has not shared home '" + name + "' with you."));
			return 0;
		}
		return HomeTeleportService.teleport(guest, home, owner.getScoreboardName() + "'s home '" + name + "'");
	}

	private static int changeAccess(CommandContext<CommandSourceStack> context, boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer owner = context.getSource().getPlayerOrException();
		String name = normalizedName(context, owner);
		if (name == null) return 0;
		Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
		List<UUID> guests = players.stream().map(ServerPlayer::getUUID).toList();
		HomeData data = HomeData.get(context.getSource().getServer());
		int changed = grant ? data.grant(owner.getUUID(), name, guests) : data.revoke(owner.getUUID(), name, guests);
		if (changed < 0) {
			owner.sendSystemMessage(Component.literal("You do not have a home named '" + name + "'."));
			return 0;
		}
		String verb = grant ? "Granted" : "Revoked";
		owner.sendSystemMessage(Component.literal(verb + " home access for " + changed + " player(s)."));
		return changed;
	}

	private static CompletableFuture<Suggestions> suggestOwnHomes(
			CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		return SharedSuggestionProvider.suggest(
				HomeData.get(context.getSource().getServer()).homeNames(player.getUUID()),
				builder
		);
	}

	private static String normalizedName(CommandContext<CommandSourceStack> context, ServerPlayer player) {
		String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
		if (VALID_NAME.matcher(name).matches()) return name;
		player.sendSystemMessage(Component.literal("Home names must be 1-32 characters using letters, numbers, underscores, or hyphens."));
		return null;
	}
}
