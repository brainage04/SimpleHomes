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
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;

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

    /**
     * The {@code usercache.json} the server writes, or empty when neither place holds one. The file
     * sits in the universe directory, which a dedicated server takes from {@code --universe}
     * ({@code Main.java:126-127}, default {@code "."}), and in the game directory on an integrated
     * one ({@code Minecraft.java:405}, {@code IntegratedServer.java:190-192}).
     *
     * <p>{@link MinecraftServer#getServerDirectory()} is {@code Path.of("")} on a dedicated server
     * ({@code MinecraftServer.java:920-922}), so it names the universe directory only while that is
     * the default; the world directory's parent is the universe directory either way
     * ({@code LevelStorageSource.createDefault} resolves the world under it,
     * {@code LevelStorageSource.java:129-131,377-379}). On an integrated server it is the game
     * directory, which holds the file, while the world directory's parent is {@code saves}.
     */
    static Optional<Path> cacheFile(MinecraftServer server) {
        return cacheFile(
                server.getServerDirectory(),
                Optional.ofNullable(server.getWorldPath(LevelResource.ROOT)));
    }

    static Optional<Path> cacheFile(Path serverDirectory, Optional<Path> worldDirectory) {
        Path inServerDirectory = serverDirectory.resolve("usercache.json");
        if (Files.isRegularFile(inServerDirectory)) {
            return Optional.of(inServerDirectory);
        }
        // getWorldPath(ROOT) is the world directory with a "/." suffix, whose parent is itself.
        return worldDirectory
                .map(Path::normalize)
                .map(Path::getParent)
                .map(parent -> parent.resolve("usercache.json"))
                .filter(Files::isRegularFile);
    }

    private static List<NameAndId> unexpired(MinecraftServer server) {
        Optional<Path> file = cacheFile(server);
        if (file.isEmpty()) {
            return List.of();
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(Files.readString(file.get(), StandardCharsets.UTF_8));
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
