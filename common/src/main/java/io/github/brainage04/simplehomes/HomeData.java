package io.github.brainage04.simplehomes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistent home locations and owner-scoped access lists. */
public final class HomeData extends SavedData {
	private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
	private static final Codec<Map<String, HomeLocation>> OWNER_HOMES_CODEC =
			Codec.unboundedMap(Codec.STRING, HomeLocation.CODEC);
	private static final Codec<Map<String, List<UUID>>> OWNER_SHARES_CODEC =
			Codec.unboundedMap(Codec.STRING, UUID_CODEC.listOf());
	private static final Codec<HomeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUID_CODEC, OWNER_HOMES_CODEC)
					.optionalFieldOf("homes", Map.of())
					.forGetter(HomeData::homesSnapshot),
			Codec.unboundedMap(UUID_CODEC, OWNER_SHARES_CODEC)
					.optionalFieldOf("shares", Map.of())
					.forGetter(HomeData::sharesSnapshot)
	).apply(instance, HomeData::new));

	public static final SavedDataType<HomeData> TYPE = new SavedDataType<>(
			SimpleHomes.of("homes"),
			HomeData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_MAP_DATA
	);

	private final Map<UUID, Map<String, HomeLocation>> homes = new HashMap<>();
	private final Map<UUID, Map<String, Set<UUID>>> shares = new HashMap<>();

	public HomeData() {
	}

	private HomeData(
			Map<UUID, Map<String, HomeLocation>> homes,
			Map<UUID, Map<String, List<UUID>>> shares
	) {
		homes.forEach((owner, entries) -> this.homes.put(owner, new HashMap<>(entries)));
		shares.forEach((owner, entries) -> {
			Map<String, Set<UUID>> ownerShares = new HashMap<>();
			entries.forEach((name, guests) -> ownerShares.put(name, new HashSet<>(guests)));
			this.shares.put(owner, ownerShares);
		});
	}

	public static HomeData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public SetResult setHome(UUID owner, String name, HomeLocation location, int maximumHomes) {
		Map<String, HomeLocation> ownerHomes = homes.computeIfAbsent(owner, ignored -> new HashMap<>());
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
				|| shares.getOrDefault(owner, Map.of()).getOrDefault(name, Set.of()).contains(guest);
	}

	public int grant(UUID owner, String name, Collection<UUID> guests) {
		if (getHome(owner, name).isEmpty()) return -1;
		Set<UUID> allowed = shares
				.computeIfAbsent(owner, ignored -> new HashMap<>())
				.computeIfAbsent(name, ignored -> new HashSet<>());
		int before = allowed.size();
		guests.stream().filter(guest -> !owner.equals(guest)).forEach(allowed::add);
		int changed = allowed.size() - before;
		if (changed > 0) setDirty();
		return changed;
	}

	public int revoke(UUID owner, String name, Collection<UUID> guests) {
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

	private Map<UUID, Map<String, HomeLocation>> homesSnapshot() {
		Map<UUID, Map<String, HomeLocation>> copy = new HashMap<>();
		homes.forEach((owner, entries) -> copy.put(owner, Map.copyOf(entries)));
		return Map.copyOf(copy);
	}

	private Map<UUID, Map<String, List<UUID>>> sharesSnapshot() {
		Map<UUID, Map<String, List<UUID>>> copy = new HashMap<>();
		shares.forEach((owner, entries) -> {
			Map<String, List<UUID>> ownerShares = new HashMap<>();
			entries.forEach((name, guests) -> ownerShares.put(name, new ArrayList<>(guests)));
			copy.put(owner, Map.copyOf(ownerShares));
		});
		return Map.copyOf(copy);
	}

	public enum SetResult {
		CREATED,
		UPDATED,
		LIMIT_REACHED
	}
}
