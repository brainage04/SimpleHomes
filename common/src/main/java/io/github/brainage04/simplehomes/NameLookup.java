package io.github.brainage04.simplehomes;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

/**
 * Asks Mojang for the account behind a name the server has not cached, off the server thread.
 *
 * <p>The server's own lookup cannot be used for this: {@code nameToIdCache().get(name)} asks Mojang
 * on the calling thread for a name it has not cached ({@code CachedUserNameToIdResolver.java:118}),
 * and the HTTP client waits up to 5 s to connect and 5 s to read ({@code
 * MinecraftClient.CONNECT_TIMEOUT_MS}, {@code READ_TIMEOUT_MS} in authlib 10.0.77), so on the
 * server thread one unknown name freezes every player. The repository is the server's own ({@code
 * Services.profileRepository()}), so a proxy and the services host the server was given apply, and
 * the answer is handed back on the server thread, where the name cache may be written.
 */
public final class NameLookup {
    /**
     * Lookups this server may make in {@link #SERVER_WINDOW}: half of Mojang's 200 per 2 minutes
     * per IP (minecraft.wiki/w/Mojang_API), leaving the other half to the server's own lookups.
     */
    private static final int SERVER_LOOKUPS = 100;

    private static final long SERVER_WINDOW = Duration.ofMinutes(2).toNanos();

    private static final Map<MinecraftServer, NameLookup> INSTANCES = new WeakHashMap<>();

    /** One lookup thread per server, so a slow answer waits for nothing else here. */
    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "SimpleHomes name lookup");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * Where a name is looked up. The server's profile repository in production, a stub in tests.
     */
    public interface Repository {
        Optional<NameAndId> find(String name);
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        FAILED,
        TOO_MANY
    }

    public record Answer(Status status, NameAndId profile) {}

    private final Repository repository;
    private final Executor worker;
    private final Executor serverThread;
    private final long[] recent = new long[SERVER_LOOKUPS];
    private int next;

    public NameLookup(Repository repository, Executor worker, Executor serverThread) {
        this.repository = repository;
        this.worker = worker;
        this.serverThread = serverThread;
    }

    public static NameLookup of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(
                    server,
                    ignored ->
                            new NameLookup(
                                    name ->
                                            server.services()
                                                    .profileRepository()
                                                    .findProfileByName(name)
                                                    .map(NameAndId::new),
                                    newExecutor(),
                                    server::execute));
        }
    }

    /**
     * Looks {@code name} up on the lookup thread and hands the answer to {@code callback} on the
     * server thread. A lookup over the server's share of Mojang's limit is answered at once with
     * {@link Status#TOO_MANY} and never reaches the network.
     */
    public void lookup(String name, Consumer<Answer> callback) {
        if (!reserve()) {
            callback.accept(new Answer(Status.TOO_MANY, null));
            return;
        }
        worker.execute(
                () -> {
                    Answer answer;
                    try {
                        answer =
                                repository
                                        .find(name)
                                        .map(profile -> new Answer(Status.FOUND, profile))
                                        .orElse(new Answer(Status.NOT_FOUND, null));
                    } catch (RuntimeException exception) {
                        SimpleHomes.LOGGER.warn(
                                "Could not look up the player name {}", name, exception);
                        answer = new Answer(Status.FAILED, null);
                    }
                    Answer delivered = answer;
                    serverThread.execute(() -> callback.accept(delivered));
                });
    }

    /** Replaces the lookup for {@code server} until {@link #reset}. A test's stub lives here. */
    public static void install(MinecraftServer server, NameLookup lookup) {
        synchronized (INSTANCES) {
            INSTANCES.put(server, lookup);
        }
    }

    public static void reset(MinecraftServer server) {
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    /** Takes one place in the server's window, or answers false when the window is full. */
    private synchronized boolean reserve() {
        long now = System.nanoTime();
        long oldest = recent[next];
        if (oldest != 0 && now - oldest < SERVER_WINDOW) return false;
        recent[next] = now;
        next = (next + 1) % recent.length;
        return true;
    }
}
