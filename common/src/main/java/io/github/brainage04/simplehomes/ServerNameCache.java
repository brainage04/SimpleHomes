package io.github.brainage04.simplehomes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * The players the server's name cache knows, read from {@code usercache.json}.
 *
 * <p>{@code nameToIdCache().get(name)} cannot answer this: for a name it has not cached it asks
 * Mojang's profile service on the server thread and writes the answer into the cache ({@code
 * CachedUserNameToIdResolver.java:63-74} and {@code 118-121}), and the interface offers no way to
 * list the cached names.
 */
final class ServerNameCache {
    private ServerNameCache() {}

    /** Every cached name that has not expired, plus the players who are online now. */
    static List<NameAndId> known(MinecraftServer server) {
        List<NameAndId> known = new ArrayList<>(unexpired(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            known.add(player.nameAndId());
        }
        return known;
    }

    private static List<NameAndId> unexpired(MinecraftServer server) {
        Path file = server.getServerDirectory().resolve("usercache.json");
        JsonElement root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
        if (!root.isJsonArray()) return List.of();
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
        Date now = new Date();
        List<NameAndId> names = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            NameAndId nameAndId = NameAndId.fromJson(entry);
            if (nameAndId == null || expired(entry, format, now)) continue;
            names.add(nameAndId);
        }
        return names;
    }

    private static boolean expired(JsonObject entry, DateFormat format, Date now) {
        JsonElement expiresOn = entry.get("expiresOn");
        if (expiresOn == null) return false;
        try {
            return !format.parse(expiresOn.getAsString()).after(now);
        } catch (ParseException exception) {
            return false;
        }
    }
}
