package io.github.brainage04.simplehomes;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public final class HomeCommandRegistration {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private HomeCommandRegistration() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("sethome")
                        .then(
                                argument("name", StringArgumentType.word())
                                        .executes(HomeCommandRegistration::setHome)));
        dispatcher.register(
                literal("home")
                        .then(
                                argument("name", StringArgumentType.word())
                                        .suggests(HomeCommandRegistration::suggestOwnHomes)
                                        .executes(HomeCommandRegistration::teleportHome)));
        dispatcher.register(
                literal("homeof")
                        .then(
                                argument("player", GameProfileArgument.gameProfile())
                                        .then(
                                                argument("name", StringArgumentType.word())
                                                        .executes(
                                                                HomeCommandRegistration
                                                                        ::teleportOtherHome))));
        dispatcher.register(
                literal("sharehome")
                        .then(
                                literal("revoke")
                                        .then(
                                                argument("players", EntityArgument.players())
                                                        .executes(
                                                                context ->
                                                                        changeAccess(
                                                                                context, true,
                                                                                "revoke")))
                                        .then(
                                                argument("name", StringArgumentType.word())
                                                        .suggests(
                                                                HomeCommandRegistration
                                                                        ::suggestOwnHomes)
                                                        .then(
                                                                argument(
                                                                                "players",
                                                                                EntityArgument
                                                                                        .players())
                                                                        .executes(
                                                                                context ->
                                                                                        changeAccess(
                                                                                                context,
                                                                                                false,
                                                                                                StringArgumentType
                                                                                                        .getString(
                                                                                                                context,
                                                                                                                "name"))))))
                        .then(
                                argument("name", StringArgumentType.word())
                                        .suggests(HomeCommandRegistration::suggestOwnHomes)
                                        .then(
                                                argument("players", EntityArgument.players())
                                                        .executes(
                                                                context ->
                                                                        changeAccess(
                                                                                context,
                                                                                true,
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "name"))))));
        dispatcher.register(
                literal("simplehomes")
                        .executes(context -> showHelp(context.getSource()))
                        .then(literal("help").executes(context -> showHelp(context.getSource()))));
    }

    private static int showHelp(CommandSourceStack source) {
        SimpleHomes.FEEDBACK.neutral(source, "Player commands:");
        SimpleHomes.FEEDBACK.neutral(
                source, "/sethome <name> - create or update a home where you stand");
        SimpleHomes.FEEDBACK.neutral(source, "/home <name> - teleport to one of your homes");
        SimpleHomes.FEEDBACK.neutral(
                source, "/sharehome <name> <players> - let players use one of your homes");
        SimpleHomes.FEEDBACK.neutral(
                source, "/sharehome revoke <name> <players> - take that access away again");
        SimpleHomes.FEEDBACK.neutral(
                source, "/homeof <player> <name> - teleport to a home someone shared with you");
        SimpleHomes.FEEDBACK.neutral(
                source, "You can have up to %d homes.", SimpleHomesConfig.maximumHomes());
        return 1;
    }

    private static int setHome(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = normalizedName(StringArgumentType.getString(context, "name"), player);
        if (name == null) return 0;
        HomeData.SetResult result =
                HomeData.get(context.getSource().getServer())
                        .setHome(
                                player.getUUID(),
                                name,
                                HomeLocation.from(player),
                                SimpleHomesConfig.maximumHomes());
        if (result == HomeData.SetResult.LIMIT_REACHED) {
            SimpleHomes.FEEDBACK.error(
                    player,
                    "You already have the maximum of %d homes.",
                    SimpleHomesConfig.maximumHomes());
            return 0;
        }
        String action = result == HomeData.SetResult.CREATED ? "Created" : "Updated";
        SimpleHomes.FEEDBACK.success(player, "%s home '%s'.", action, name);
        return 1;
    }

    private static int teleportHome(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = normalizedName(StringArgumentType.getString(context, "name"), player);
        if (name == null) return 0;
        HomeLocation home =
                HomeData.get(context.getSource().getServer())
                        .getHome(player.getUUID(), name)
                        .orElse(null);
        if (home == null) {
            SimpleHomes.FEEDBACK.error(player, "You do not have a home named '%s'.", name);
            return 0;
        }
        return HomeTeleportService.teleport(player, home, "home '" + name + "'");
    }

    private static int teleportOtherHome(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer guest = context.getSource().getPlayerOrException();
        Collection<NameAndId> owners = GameProfileArgument.getGameProfiles(context, "player");
        if (owners.size() != 1) {
            SimpleHomes.FEEDBACK.error(guest, "Select exactly one home owner.");
            return 0;
        }
        NameAndId owner = owners.iterator().next();
        String name = normalizedName(StringArgumentType.getString(context, "name"), guest);
        if (name == null) return 0;
        HomeData data = HomeData.get(context.getSource().getServer());
        HomeLocation home = data.getHome(owner.id(), name).orElse(null);
        if (home == null) {
            SimpleHomes.FEEDBACK.error(
                    guest, "%s does not have a home named '%s'.", owner.name(), name);
            return 0;
        }
        if (!data.allows(owner.id(), name, guest.getUUID())) {
            SimpleHomes.FEEDBACK.error(
                    guest, "%s has not shared home '%s' with you.", owner.name(), name);
            return 0;
        }
        return HomeTeleportService.teleport(guest, home, owner.name() + "'s home '" + name + "'");
    }

    private static int changeAccess(
            CommandContext<CommandSourceStack> context, boolean grant, String rawName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer owner = context.getSource().getPlayerOrException();
        String name = normalizedName(rawName, owner);
        if (name == null) return 0;
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        List<UUID> guests = players.stream().map(ServerPlayer::getUUID).toList();
        HomeData data = HomeData.get(context.getSource().getServer());
        int changed =
                grant
                        ? data.grant(owner.getUUID(), name, guests)
                        : data.revoke(owner.getUUID(), name, guests);
        if (changed < 0) {
            SimpleHomes.FEEDBACK.error(owner, "You do not have a home named '%s'.", name);
            return 0;
        }
        String verb = grant ? "Granted" : "Revoked";
        if (changed == 0) {
            SimpleHomes.FEEDBACK.neutral(owner, "%s home access for 0 player(s).", verb);
        } else {
            SimpleHomes.FEEDBACK.success(owner, "%s home access for %d player(s).", verb, changed);
        }
        return changed;
    }

    private static CompletableFuture<Suggestions> suggestOwnHomes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return SharedSuggestionProvider.suggest(
                HomeData.get(context.getSource().getServer()).homeNames(player.getUUID()), builder);
    }

    private static String normalizedName(String rawName, ServerPlayer player) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (VALID_NAME.matcher(name).matches()) return name;
        SimpleHomes.FEEDBACK.error(
                player,
                "Home names must be 1-32 characters using letters, numbers, underscores, or hyphens.");
        return null;
    }
}
