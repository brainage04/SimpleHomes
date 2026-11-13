package io.github.brainage04.simplehomes;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.brainage04.brainagelib.help.HelpPage;
import java.util.Collection;
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
import net.minecraft.server.players.UserNameToIdResolver;

public final class HomeCommandRegistration {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    /** The word /sharehome uses for taking access away, so it cannot also be a home name. */
    private static final String RESERVED_NAME = "revoke";

    private HomeCommandRegistration() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("sethome")
                        .then(
                                argument("name", StringArgumentType.word())
                                        .executes(HomeCommandRegistration::setHome)));
        dispatcher.register(
                literal("home")
                        .then(ownHomeName().executes(HomeCommandRegistration::teleportHome)));
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
                                        // A home named 'revoke' cannot be reached the usual way,
                                        // because brigadier parses this literal first. This branch
                                        // keeps a home saved under that name by a build that still
                                        // allowed it usable; it suggests nothing, so that a Tab
                                        // after "/sharehome revoke " lists home names instead of
                                        // the online players an entity argument would offer
                                        // (A-0795).
                                        .then(
                                                argument("players", EntityArgument.players())
                                                        .suggests(
                                                                (context, builder) ->
                                                                        Suggestions.empty())
                                                        .executes(
                                                                context ->
                                                                        changeAccess(
                                                                                context,
                                                                                true,
                                                                                RESERVED_NAME)))
                                        .then(
                                                ownHomeName()
                                                        .then(
                                                                argument(
                                                                                "players",
                                                                                GameProfileArgument
                                                                                        .gameProfile())
                                                                        .suggests(
                                                                                HomeCommandRegistration
                                                                                        ::suggestGuests)
                                                                        .executes(
                                                                                HomeCommandRegistration
                                                                                        ::revokeNamedHome))))
                        .then(
                                ownHomeName()
                                        .then(
                                                argument("players", EntityArgument.players())
                                                        .executes(
                                                                HomeCommandRegistration
                                                                        ::grantNamedHome))));
        HelpPage.create(SimpleHomes.FEEDBACK, "simplehomes")
                .line("/sethome <name> - create or update a home where you stand")
                .line("/home <name> - teleport to one of your homes")
                .line("/sharehome <name> <players> - let players use one of your homes")
                .line("/sharehome revoke <name> <players> - take that access away again")
                .line("/homeof <player> <name> - teleport to a home someone shared with you")
                .line("You can have up to %d homes.", SimpleHomesConfig.maximumHomes())
                .register(dispatcher);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> ownHomeName() {
        return argument("name", StringArgumentType.word())
                .suggests(HomeCommandRegistration::suggestOwnHomes);
    }

    private static int grantNamedHome(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return changeAccess(context, true, StringArgumentType.getString(context, "name"));
    }

    private static int revokeNamedHome(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return changeAccess(context, false, StringArgumentType.getString(context, "name"));
    }

    private static int setHome(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = normalizedName(StringArgumentType.getString(context, "name"), player);
        if (name == null) return 0;
        if (name.equals(RESERVED_NAME)) {
            SimpleHomes.FEEDBACK.error(
                    player,
                    "The name '%s' is reserved by /sharehome %s. Choose another name.",
                    RESERVED_NAME,
                    RESERVED_NAME);
            return 0;
        }
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
        String name = normalizedName(StringArgumentType.getString(context, "name"), guest);
        if (name == null) return 0;
        HomeData data = HomeData.get(context.getSource().getServer());
        Collection<NameAndId> owners =
                players(context, "player", data.ownersAllowing(name, guest.getUUID()));
        if (owners.size() > 1) {
            SimpleHomes.FEEDBACK.error(guest, "Select exactly one home owner.");
            return 0;
        }
        NameAndId owner = owners.isEmpty() ? null : owners.iterator().next();
        if (owner == null || !data.allows(owner.id(), name, guest.getUUID())) {
            String ownerName = owner == null ? typedArgument(context, "player") : owner.name();
            SimpleHomes.FEEDBACK.error(
                    guest, "%s has not shared home '%s' with you.", ownerName, name);
            return 0;
        }
        HomeLocation home = data.getHome(owner.id(), name).orElse(null);
        if (home == null) {
            SimpleHomes.FEEDBACK.error(
                    guest, "%s does not have a home named '%s'.", owner.name(), name);
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
        HomeData data = HomeData.get(context.getSource().getServer());
        Collection<UUID> guests =
                grant
                        ? EntityArgument.getPlayers(context, "players").stream()
                                .map(ServerPlayer::getUUID)
                                .toList()
                        : players(context, "players", data.guests(owner.getUUID(), name)).stream()
                                .map(NameAndId::id)
                                .toList();
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

    private static CompletableFuture<Suggestions> suggestGuests(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer owner = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
        UserNameToIdResolver names = context.getSource().getServer().services().nameToIdCache();
        return SharedSuggestionProvider.suggest(
                HomeData.get(context.getSource().getServer()).guests(owner.getUUID(), name).stream()
                        .flatMap(guest -> names.get(guest).stream())
                        .map(NameAndId::name)
                        .toList(),
                builder);
    }

    /**
     * The players a {@link GameProfileArgument} names: a selector's online players, or those of
     * {@code candidates} whose cached name is the name typed.
     */
    private static Collection<NameAndId> players(
            CommandContext<CommandSourceStack> context,
            String argument,
            Collection<UUID> candidates)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        GameProfileArgument.Result result =
                context.getArgument(argument, GameProfileArgument.Result.class);
        if (result instanceof GameProfileArgument.SelectorResult) {
            return result.getNames(context.getSource());
        }
        // A typed name never goes through GameProfileArgument.getNames: for a name the server has
        // not cached it asks Mojang's profile service on the server thread and writes the answer to
        // usercache.json (GameProfileArgument.java:63-66, CachedUserNameToIdResolver.java:63-69,
        // 118-120). The lookup by UUID only reads the cache
        // (CachedUserNameToIdResolver.java:135-143).
        String typed = typedArgument(context, argument);
        UserNameToIdResolver names = context.getSource().getServer().services().nameToIdCache();
        return candidates.stream()
                .flatMap(candidate -> names.get(candidate).stream())
                .filter(player -> player.name().equalsIgnoreCase(typed))
                .toList();
    }

    private static String typedArgument(
            CommandContext<CommandSourceStack> context, String argument) {
        for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
            if (node.getNode().getName().equals(argument)) {
                return node.getRange().get(context.getInput());
            }
        }
        throw new IllegalArgumentException("No argument named " + argument);
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
