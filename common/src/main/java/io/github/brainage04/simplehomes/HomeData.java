package io.github.brainage04.simplehomes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private static final int NAMES_PER_ACCOUNT = 10;

    /**
     * How many accounts that are not guests keep a join history. The cap matches the 1000 profiles
     * {@code usercache.json} keeps (CachedUserNameToIdResolver.java:40,172), which is the window
     * {@code ServerNameCache} can resolve a name in.
     */
    private static final int NON_GUEST_HISTORIES = 1000;

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

    /** Every account some owner has shared a home with. */
    public Set<UUID> allGuests() {
        Set<UUID> guests = new HashSet<>();
        shares.values().forEach(entries -> entries.values().forEach(guests::addAll));
        return guests;
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
        if (changed == 0) return 0;
        // A guest every owner has revoked can never match again, so its history is dropped rather
        // than rewritten into the saved data on every later change.
        guests.stream()
                .filter(
                        guest ->
                                shares.values().stream()
                                        .flatMap(entries -> entries.values().stream())
                                        .noneMatch(set -> set.contains(guest)))
                .forEach(guestNames::remove);
        setDirty();
        return changed;
    }

    /**
     * Records that the server has seen {@code name} for {@code guest} now, keeping the newest
     * {@link #NAMES_PER_ACCOUNT} names. A name seen again moves to the front with the new time.
     */
    public void sawName(UUID guest, String name, long now) {
        List<SeenName> seen = guestNames.computeIfAbsent(guest, ignored -> new ArrayList<>());
        seen.removeIf(entry -> entry.name().equalsIgnoreCase(name));
        seen.addFirst(new SeenName(name, now));
        while (seen.size() > NAMES_PER_ACCOUNT) seen.removeLast();
        setDirty();
    }

    /**
     * Records {@code name} as one the account has held, without claiming the server saw the player
     * under it: an owner typing a name is not a sighting, and "last seen" is the last join. A name
     * already recorded keeps its place and its time. A full history drops its oldest typed name
     * first, and its oldest join name when it holds no typed name, so a newly typed name is always
     * recorded.
     */
    public void recordName(UUID guest, String name) {
        List<SeenName> seen = guestNames.computeIfAbsent(guest, ignored -> new ArrayList<>());
        if (seen.stream().anyMatch(entry -> entry.name().equalsIgnoreCase(name))) return;
        if (seen.size() >= NAMES_PER_ACCOUNT) {
            // Typed names sit after the joins, oldest first, so the first one found is the oldest.
            int oldestTyped = -1;
            for (int index = 0; index < seen.size(); index++) {
                if (seen.get(index).lastSeen() == 0) {
                    oldestTyped = index;
                    break;
                }
            }
            seen.remove(oldestTyped >= 0 ? oldestTyped : seen.size() - 1);
        }
        seen.addLast(new SeenName(name, 0));
        setDirty();
    }

    /**
     * The names recorded for {@code guest}: the joins newest first, then the names an owner typed,
     * oldest first.
     */
    public List<SeenName> names(UUID guest) {
        return List.copyOf(guestNames.getOrDefault(guest, List.of()));
    }

    /**
     * Records a join for any account, guest or not, so a player granted access later still shows
     * the login they made before it. {@link #pruneNames} keeps the map bounded.
     */
    public void recordJoin(UUID player, String name, long now) {
        sawName(player, name, now);
    }

    /**
     * Drops the history of accounts that are neither in {@code keep} nor among the newest {@link
     * #NON_GUEST_HISTORIES} entries, so recording every join cannot grow the saved data without
     * bound.
     */
    public void pruneNames(Set<UUID> keep) {
        List<UUID> droppable =
                guestNames.entrySet().stream()
                        .filter(entry -> !keep.contains(entry.getKey()))
                        .sorted(
                                Comparator.comparingLong(
                                        entry -> entry.getValue().getFirst().lastSeen()))
                        .map(Map.Entry::getKey)
                        .toList();
        int excess = droppable.size() - NON_GUEST_HISTORIES;
        if (excess <= 0) return;
        droppable.stream().limit(excess).forEach(guestNames::remove);
        setDirty();
    }

    /** The guests of {@code owner}'s home {@code name} whose history holds {@code typed}. */
    public List<UUID> guestsNamed(UUID owner, String name, String typed) {
        return guests(owner, name).stream()
                .filter(
                        guest ->
                                names(guest).stream()
                                        .anyMatch(seen -> seen.name().equalsIgnoreCase(typed)))
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
