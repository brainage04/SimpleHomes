package io.github.brainage04.simplehomes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HomeData extends SavedData {
    /** A name the server has seen for an account, and when it last saw it (epoch millis). */
    public record SeenName(String name, long lastSeen) {
        public static final Codec<SeenName> CODEC =
                RecordCodecBuilder.create(
                        instance ->
                                instance.group(
                                                Codec.STRING
                                                        .fieldOf("name")
                                                        .forGetter(SeenName::name),
                                                Codec.LONG
                                                        .fieldOf("lastSeen")
                                                        .forGetter(SeenName::lastSeen))
                                        .apply(instance, SeenName::new));
    }

    /** How many names one account keeps. A player who renames often keeps the recent ones. */
    private static final int NAMES_PER_GUEST = 10;

    private static final Codec<Map<String, HomeLocation>> OWNER_HOMES_CODEC =
            Codec.unboundedMap(Codec.STRING, HomeLocation.CODEC);
    private static final Codec<Map<String, List<UUID>>> OWNER_SHARES_CODEC =
            Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC.listOf());
    private static final Codec<Map<UUID, List<SeenName>>> GUEST_NAMES_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, SeenName.CODEC.listOf());
    private static final Codec<HomeData> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.unboundedMap(
                                                            UUIDUtil.STRING_CODEC,
                                                            OWNER_HOMES_CODEC)
                                                    .optionalFieldOf("homes", Map.of())
                                                    .forGetter(HomeData::homesSnapshot),
                                            Codec.unboundedMap(
                                                            UUIDUtil.STRING_CODEC,
                                                            OWNER_SHARES_CODEC)
                                                    .optionalFieldOf("shares", Map.of())
                                                    .forGetter(HomeData::sharesSnapshot),
                                            GUEST_NAMES_CODEC
                                                    .optionalFieldOf("guestNames", Map.of())
                                                    .forGetter(HomeData::namesSnapshot))
                                    .apply(instance, HomeData::new));

    public static final SavedDataType<HomeData> TYPE =
            new SavedDataType<>(
                    SimpleHomes.of("homes"),
                    HomeData::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<UUID, Map<String, HomeLocation>> homes = new HashMap<>();
    private final Map<UUID, Map<String, Set<UUID>>> shares = new HashMap<>();
    private final Map<UUID, List<SeenName>> guestNames = new HashMap<>();

    public HomeData() {}

    private HomeData(
            Map<UUID, Map<String, HomeLocation>> homes,
            Map<UUID, Map<String, List<UUID>>> shares,
            Map<UUID, List<SeenName>> guestNames) {
        homes.forEach((owner, entries) -> this.homes.put(owner, new HashMap<>(entries)));
        shares.forEach(
                (owner, entries) -> {
                    Map<String, Set<UUID>> ownerShares = new HashMap<>();
                    entries.forEach((name, guests) -> ownerShares.put(name, new HashSet<>(guests)));
                    this.shares.put(owner, ownerShares);
                });
        guestNames.forEach((guest, seen) -> this.guestNames.put(guest, new ArrayList<>(seen)));
    }

    public static HomeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public SetResult setHome(UUID owner, String name, HomeLocation location, int maximumHomes) {
        Map<String, HomeLocation> ownerHomes =
                homes.computeIfAbsent(owner, ignored -> new HashMap<>());
        boolean exists = ownerHomes.containsKey(name);
        if (!exists && ownerHomes.size() >= maximumHomes) {
            return SetResult.LIMIT_REACHED;
        }
        ownerHomes.put(name, location);
        setDirty();
        return exists ? SetResult.UPDATED : SetResult.CREATED;
    }

    public Optional<HomeLocation> getHome(UUID owner, String name) {
        return Optional.ofNullable(homes.getOrDefault(owner, Map.of()).get(name));
    }

    public List<String> homeNames(UUID owner) {
        return homes.getOrDefault(owner, Map.of()).keySet().stream().sorted().toList();
    }

    public boolean allows(UUID owner, String name, UUID guest) {
        return owner.equals(guest)
                || shares.getOrDefault(owner, Map.of())
                        .getOrDefault(name, Set.of())
                        .contains(guest);
    }

    /** The owners whose home {@code name} {@code guest} may use, {@code guest} included. */
    public List<UUID> ownersAllowing(String name, UUID guest) {
        return homes.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(name))
                .map(Map.Entry::getKey)
                .filter(owner -> allows(owner, name, guest))
                .toList();
    }

    public List<UUID> guests(UUID owner, String name) {
        return List.copyOf(shares.getOrDefault(owner, Map.of()).getOrDefault(name, Set.of()));
    }

    public int grant(UUID owner, String name, Collection<UUID> guests) {
        if (getHome(owner, name).isEmpty()) return -1;
        Set<UUID> allowed =
                shares.computeIfAbsent(owner, ignored -> new HashMap<>())
                        .computeIfAbsent(name, ignored -> new HashSet<>());
        int before = allowed.size();
        guests.stream().filter(guest -> !owner.equals(guest)).forEach(allowed::add);
        int changed = allowed.size() - before;
        if (changed > 0) setDirty();
        return changed;
    }

    public int revoke(UUID owner, String name, Collection<UUID> guests) {
        if (getHome(owner, name).isEmpty()) return -1;
        Map<String, Set<UUID>> ownerShares = shares.get(owner);
        if (ownerShares == null) return 0;
        Set<UUID> allowed = ownerShares.get(name);
        if (allowed == null) return 0;
        int before = allowed.size();
        allowed.removeAll(guests);
        int changed = before - allowed.size();
        if (allowed.isEmpty()) ownerShares.remove(name);
        if (ownerShares.isEmpty()) shares.remove(owner);
        if (changed > 0) setDirty();
        return changed;
    }

    /**
     * Records that the server has seen {@code name} for {@code guest} now, keeping the newest
     * {@link #NAMES_PER_GUEST} names. A name seen again moves to the front with the new time.
     */
    public void sawName(UUID guest, String name, long now) {
        List<SeenName> seen = guestNames.computeIfAbsent(guest, ignored -> new ArrayList<>());
        seen.removeIf(entry -> entry.name().equalsIgnoreCase(name));
        seen.addFirst(new SeenName(name, now));
        while (seen.size() > NAMES_PER_GUEST) seen.removeLast();
        setDirty();
    }

    /** The names the server has seen for {@code guest}, newest first. */
    public List<SeenName> names(UUID guest) {
        return List.copyOf(guestNames.getOrDefault(guest, List.of()));
    }

    /** The guests of {@code owner}'s home {@code name} whose history holds {@code typed}. */
    public List<UUID> guestsNamed(UUID owner, String name, String typed) {
        return guests(owner, name).stream()
                .filter(guest -> names(guest).stream().anyMatch(seen -> seen.name().equalsIgnoreCase(typed)))
                .toList();
    }

    private Map<UUID, Map<String, HomeLocation>> homesSnapshot() {
        Map<UUID, Map<String, HomeLocation>> copy = new HashMap<>();
        homes.forEach((owner, entries) -> copy.put(owner, Map.copyOf(entries)));
        return Map.copyOf(copy);
    }

    private Map<UUID, Map<String, List<UUID>>> sharesSnapshot() {
        Map<UUID, Map<String, List<UUID>>> copy = new HashMap<>();
        shares.forEach(
                (owner, entries) -> {
                    Map<String, List<UUID>> ownerShares = new HashMap<>();
                    entries.forEach(
                            (name, guests) -> ownerShares.put(name, new ArrayList<>(guests)));
                    copy.put(owner, Map.copyOf(ownerShares));
                });
        return Map.copyOf(copy);
    }

    private Map<UUID, List<SeenName>> namesSnapshot() {
        Map<UUID, List<SeenName>> copy = new HashMap<>();
        guestNames.forEach((guest, seen) -> copy.put(guest, List.copyOf(seen)));
        return Map.copyOf(copy);
    }

    public enum SetResult {
        CREATED,
        UPDATED,
        LIMIT_REACHED
    }
}
