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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserNameToIdResolver;

public final class HomeCommandRegistration {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    /** The word /sharehome uses for taking access away, so it cannot also be a home name. */
    private static final String RESERVED_NAME = "revoke";

    private static final DateTimeFormatter LAST_SEEN =
            DateTimeFormatter.ofPattern("d MMM", Locale.ROOT).withZone(ZoneId.systemDefault());

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
                                                argument(
                                                                "players",
                                                                GameProfileArgument.gameProfile())
                                                        .executes(
                                                                HomeCommandRegistration
                                                                        ::grantNamedHome))));
        HelpPage.create(SimpleHomes.FEEDBACK, "simplehomes")
                .line("/sethome <name> - create or update a home where you stand")
                .line("/home <name> - teleport to one of your homes")
                .line(
                        "/sharehome <name> <players> - let players use one of your homes,"
                                + " online or offline")
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

    private static int setHome(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = SimpleHomes.FEEDBACK.requirePlayer(context.getSource());
        if (player == null) return 0;
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

    private static int teleportHome(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = SimpleHomes.FEEDBACK.requirePlayer(context.getSource());
        if (player == null) return 0;
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
        ServerPlayer guest = SimpleHomes.FEEDBACK.requirePlayer(context.getSource());
        if (guest == null) return 0;
        String name = normalizedName(StringArgumentType.getString(context, "name"), guest);
        if (name == null) return 0;
        HomeData data = HomeData.get(context.getSource().getServer());
        Collection<NameAndId> owners =
                typedMatches(
                        context,
                        "player",
                        cachedNames(
                                context.getSource().getServer(),
                                data.ownersAllowing(name, guest.getUUID())));
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
        ServerPlayer owner = SimpleHomes.FEEDBACK.requirePlayer(context.getSource());
        if (owner == null) return 0;
        String name = normalizedName(rawName, owner);
        if (name == null) return 0;
        MinecraftServer server = context.getSource().getServer();
        HomeData data = HomeData.get(server);
        if (data.getHome(owner.getUUID(), name).isEmpty()) {
            SimpleHomes.FEEDBACK.error(owner, "You do not have a home named '%s'.", name);
            return 0;
        }
        long wait = ShareRateLimit.of(server).refuse(owner.getUUID());
        if (wait > 0) {
            SimpleHomes.FEEDBACK.error(
                    owner, "You can use /sharehome again in %s.", ShareRateLimit.describe(wait));
            return 0;
        }
        GameProfileArgument.Result result =
                context.getArgument("players", GameProfileArgument.Result.class);
        if (result instanceof GameProfileArgument.SelectorResult) {
            return apply(owner, data, name, grant, result.getNames(context.getSource()));
        }
        String typed = typedArgument(context, "players");
        return grant
                ? grantTyped(server, owner, data, name, typed)
                : revokeTyped(server, owner, data, name, typed);
    }

    /**
     * Grants to the player a typed name belongs to. A name the server has cached is answered at
     * once; any other name is looked up off the server thread and the grant finishes when the
     * answer arrives.
     */
    private static int grantTyped(
            MinecraftServer server, ServerPlayer owner, HomeData data, String name, String typed) {
        List<NameAndId> matches = named(server, typed);
        if (!matches.isEmpty()) return apply(owner, data, name, true, matches);
        UUID id = parseUuid(typed);
        if (id != null) {
            List<NameAndId> cached = List.copyOf(cachedNames(server, List.of(id)));
            if (!cached.isEmpty()) return apply(owner, data, name, true, cached);
            SimpleHomes.FEEDBACK.error(
                    owner, "No player with the id '%s' has joined this server.", typed);
            return 0;
        }
        SimpleHomes.FEEDBACK.neutral(owner, "Looking up %s...", typed);
        UUID ownerId = owner.getUUID();
        // The answer arrives on the server thread later, so the command's result is 1 (accepted)
        // unless the lookup answered before it returned, as a test's stub does.
        int[] outcome = {1};
        NameLookup.of(server)
                .lookup(
                        typed,
                        answer -> {
                            ServerPlayer current = server.getPlayerList().getPlayer(ownerId);
                            if (current == null) return;
                            outcome[0] = report(server, current, name, typed, answer);
                        });
        return outcome[0];
    }

    private static int report(
            MinecraftServer server,
            ServerPlayer owner,
            String name,
            String typed,
            NameLookup.Answer answer) {
        switch (answer.status()) {
            case FOUND -> {
                NameAndId profile = answer.profile();
                // add() also rewrites usercache.json (CachedUserNameToIdResolver.java:86-95).
                server.services().nameToIdCache().add(profile);
                return apply(owner, HomeData.get(server), name, true, List.of(profile));
            }
            case NOT_FOUND -> {
                SimpleHomes.FEEDBACK.error(owner, "Mojang has no player named '%s'.", typed);
                return 0;
            }
            case FAILED -> {
                SimpleHomes.FEEDBACK.error(
                        owner, "Could not look up '%s' right now. Try again later.", typed);
                return 0;
            }
            case STALE -> {
                // Caching the name makes the retry answer at once, without another lookup.
                server.services().nameToIdCache().add(answer.profile());
                SimpleHomes.FEEDBACK.error(
                        owner,
                        "Found '%s', but the lookup took too long to act on. Run the command again.",
                        typed);
                return 0;
            }
            default -> {
                SimpleHomes.FEEDBACK.error(
                        owner,
                        "Too many name lookups on this server right now. Try again in a minute.");
                return 0;
            }
        }
    }

    /**
     * Revokes from the guest a typed name belongs to. A name that matches more than one guest
     * revokes nothing and lists each match with a button that revokes by id, because a renamed
     * player and a new holder of the name are different accounts.
     */
    private static int revokeTyped(
            MinecraftServer server, ServerPlayer owner, HomeData data, String name, String typed) {
        UUID ownerId = owner.getUUID();
        UUID id = parseUuid(typed);
        if (id != null) {
            if (!data.guests(ownerId, name).contains(id)) {
                SimpleHomes.FEEDBACK.error(
                        owner, "That player does not have access to '%s'.", name);
                return 0;
            }
            return report(owner, data.revoke(ownerId, name, List.of(id)), false);
        }
        List<UUID> matches = new ArrayList<>(data.guestsNamed(ownerId, name, typed));
        for (NameAndId cached : cachedNames(server, data.guests(ownerId, name))) {
            if (cached.name().equalsIgnoreCase(typed) && !matches.contains(cached.id())) {
                matches.add(cached.id());
            }
        }
        if (matches.isEmpty()) {
            SimpleHomes.FEEDBACK.error(owner, "No guest of '%s' is named '%s'.", name, typed);
            return 0;
        }
        if (matches.size() == 1) {
            return report(owner, data.revoke(ownerId, name, List.of(matches.getFirst())), false);
        }
        SimpleHomes.FEEDBACK.neutral(
                owner,
                "%d guests of '%s' have been called '%s'. Choose the one to revoke:",
                matches.size(),
                name,
                typed);
        for (UUID guest : matches) {
            owner.sendSystemMessage(describe(data, guest, name));
        }
        return 0;
    }

    /** One line of the ambiguous revoke list: the guest, its other names, and a revoke button. */
    private static Component describe(HomeData data, UUID guest, String home) {
        List<HomeData.SeenName> seen = data.names(guest);
        String current = seen.isEmpty() ? guest.toString() : seen.getFirst().name();
        MutableComponent line = Component.literal(" " + current).withStyle(ChatFormatting.WHITE);
        List<String> previous =
                seen.stream()
                        .map(HomeData.SeenName::name)
                        .filter(name -> !name.equalsIgnoreCase(current))
                        .toList();
        if (!previous.isEmpty()) {
            line.append(
                    Component.literal(" (previous name " + String.join(", ", previous) + ")")
                            .withStyle(ChatFormatting.GRAY));
        }
        if (!seen.isEmpty()) {
            line.append(
                    Component.literal(
                                    " last seen "
                                            + LAST_SEEN.format(
                                                    Instant.ofEpochMilli(
                                                            seen.getFirst().lastSeen())))
                            .withStyle(ChatFormatting.GRAY));
        }
        line.append(
                Component.literal(" [Revoke]")
                        .withStyle(
                                style ->
                                        style.withColor(ChatFormatting.GREEN)
                                                .withClickEvent(
                                                        new ClickEvent.RunCommand(
                                                                "/sharehome revoke "
                                                                        + home
                                                                        + " "
                                                                        + guest))));
        return line;
    }

    private static int apply(
            ServerPlayer owner,
            HomeData data,
            String name,
            boolean grant,
            Collection<NameAndId> named) {
        long now = System.currentTimeMillis();
        for (NameAndId profile : named) data.sawName(profile.id(), profile.name(), now);
        Collection<UUID> guests = named.stream().map(NameAndId::id).toList();
        int changed =
                grant
                        ? data.grant(owner.getUUID(), name, guests)
                        : data.revoke(owner.getUUID(), name, guests);
        if (changed < 0) {
            SimpleHomes.FEEDBACK.error(owner, "You do not have a home named '%s'.", name);
            return 0;
        }
        return report(owner, changed, grant);
    }

    private static int report(ServerPlayer owner, int changed, boolean grant) {
        String verb = grant ? "Granted" : "Revoked";
        if (changed == 0) {
            SimpleHomes.FEEDBACK.neutral(owner, "%s home access for 0 player(s).", verb);
        } else {
            SimpleHomes.FEEDBACK.success(owner, "%s home access for %d player(s).", verb, changed);
        }
        return changed;
    }

    // A completion request from a source that is not a player gets no suggestions and no message:
    // requirePlayer would answer a Tab press with chat.
    private static CompletableFuture<Suggestions> suggestOwnHomes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return Suggestions.empty();
        return SharedSuggestionProvider.suggest(
                HomeData.get(context.getSource().getServer()).homeNames(player.getUUID()), builder);
    }

    private static CompletableFuture<Suggestions> suggestGuests(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer owner = context.getSource().getPlayer();
        if (owner == null) return Suggestions.empty();
        String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
        HomeData data = HomeData.get(context.getSource().getServer());
        List<String> names = new ArrayList<>();
        for (UUID guest : data.guests(owner.getUUID(), name)) {
            data.names(guest).forEach(seen -> names.add(seen.name()));
        }
        names.addAll(
                cachedNames(context.getSource().getServer(), data.guests(owner.getUUID(), name))
                        .stream()
                        .map(NameAndId::name)
                        .toList());
        return SharedSuggestionProvider.suggest(names.stream().distinct().toList(), builder);
    }

    /**
     * The players a {@link GameProfileArgument} names: a selector's online players, or those of
     * {@code candidates} whose cached name is the name typed.
     */
    private static Collection<NameAndId> typedMatches(
            CommandContext<CommandSourceStack> context,
            String argument,
            Collection<NameAndId> candidates)
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
        return candidates.stream().filter(player -> player.name().equalsIgnoreCase(typed)).toList();
    }

    /**
     * The players a typed name belongs to: the online ones when any is online, otherwise the cached
     * ones. A name can belong to several accounts over time, and the player standing here now is
     * the one meant.
     */
    private static List<NameAndId> named(MinecraftServer server, String typed) {
        List<NameAndId> online =
                server.getPlayerList().getPlayers().stream()
                        .map(ServerPlayer::nameAndId)
                        .filter(player -> player.name().equalsIgnoreCase(typed))
                        .toList();
        if (!online.isEmpty()) return online;
        return ServerNameCache.known(server).stream()
                .filter(player -> player.name().equalsIgnoreCase(typed))
                .toList();
    }

    private static Collection<NameAndId> cachedNames(MinecraftServer server, Collection<UUID> ids) {
        UserNameToIdResolver names = server.services().nameToIdCache();
        return ids.stream().flatMap(id -> names.get(id).stream()).toList();
    }

    private static UUID parseUuid(String typed) {
        try {
            return UUID.fromString(typed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
