package io.github.brainage04.simplehomes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;
import net.minecraft.server.MinecraftServer;

/**
 * How often one player may use /sharehome: once a second, 20 a minute, 400 an hour.
 *
 * <p>Server-wide rather than per instance, because a reload must not hand a player a fresh
 * allowance. Only read and written on the server thread.
 */
public final class ShareRateLimit {
    private static final long SECOND = 1_000_000_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    private static final Map<MinecraftServer, ShareRateLimit> INSTANCES = new WeakHashMap<>();
    private static final Map<MinecraftServer, ShareRateLimit> LISTINGS = new WeakHashMap<>();

    private final LongSupplier clock;
    private final Map<UUID, Deque<Long>> used = new HashMap<>();

    public ShareRateLimit(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * The allowance the ambiguous revoke's list uses, kept apart from {@link #of}: the list must
     * not charge the player the click its own button needs, and it must still be bounded.
     */
    public static ShareRateLimit listing(MinecraftServer server) {
        synchronized (LISTINGS) {
            return LISTINGS.computeIfAbsent(
                    server, ignored -> new ShareRateLimit(System::nanoTime));
        }
    }

    public static ShareRateLimit of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(
                    server, ignored -> new ShareRateLimit(System::nanoTime));
        }
    }

    /**
     * The time the player must wait before this use, or zero when the use is allowed and counted. A
     * refused use is not counted, so a player who keeps trying is not pushed further out.
     */
    public long refuse(UUID player) {
        long now = clock.getAsLong();
        Deque<Long> uses = used.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        while (!uses.isEmpty() && now - uses.peekFirst() >= HOUR) uses.removeFirst();
        long wait = 0;
        wait = Math.max(wait, waitFor(uses, 1, SECOND, now));
        wait = Math.max(wait, waitFor(uses, 20, MINUTE, now));
        wait = Math.max(wait, waitFor(uses, 400, HOUR, now));
        if (wait == 0) uses.addLast(now);
        return wait;
    }

    private static long waitFor(Deque<Long> uses, int limit, long window, long now) {
        if (uses.size() < limit) return 0;
        // The newest uses sit at the end; the one that has to expire is the limit-th from the end.
        int skip = uses.size() - limit;
        long blockUntil = 0;
        for (long use : uses) {
            if (skip-- > 0) continue;
            blockUntil = use + window;
            break;
        }
        return Math.max(0, blockUntil - now);
    }

    public static String describe(long nanos) {
        long seconds = (nanos + SECOND - 1) / SECOND;
        return seconds == 1 ? "1 second" : seconds + " seconds";
    }

    /** Forgets every use, for a GameTest whose steps all run within one second. */
    public void clear() {
        used.clear();
    }

    /**
     * Clears every allowance for {@code server}: the share allowance, and the one the ambiguous
     * list uses.
     */
    public static void clearAll(MinecraftServer server) {
        of(server).clear();
        listing(server).clear();
    }
}
