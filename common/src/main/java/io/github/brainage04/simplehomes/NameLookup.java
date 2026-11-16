package io.github.brainage04.simplehomes;

import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.services.ProfileNotFoundException;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.StringUtil;

/**
 * Asks Mojang for the account behind a name the server has not cached, off the server thread.
 *
 * <p>The server's own lookup cannot be used for this: {@code nameToIdCache().get(name)} asks Mojang
 * on the calling thread for a name it has not cached ({@code CachedUserNameToIdResolver.java:118}),
 * and the HTTP client waits up to 5 s to connect and 5 s to read ({@code
 * MinecraftClient.CONNECT_TIMEOUT_MS}, {@code READ_TIMEOUT_MS} in authlib 10.0.77), so on the
 * server thread one unknown name freezes every player. The repository is the server's own ({@code
 * Services.profileRepository()}), so a proxy and the services host the server was given apply.
 */
public final class NameLookup {
    /**
     * Lookups this server may make in {@link #SERVER_WINDOW}: half of Mojang's 200 per 2 minutes
     * per IP (minecraft.wiki/w/Mojang_API), leaving the other half to the server's own lookups.
     */
    private static final int SERVER_LOOKUPS = 100;

    private static final long SERVER_WINDOW = Duration.ofMinutes(2).toNanos();

    /**
     * How long an answer is still worth applying. A lookup can wait behind others on the one lookup
     * thread, and an answer that arrives after the player has moved on is dropped.
     */
    private static final long MAX_AGE = Duration.ofSeconds(30).toNanos();

    private static final Map<MinecraftServer, NameLookup> INSTANCES = new WeakHashMap<>();

    /** One lookup thread for the whole JVM: a slow answer delays only the lookups behind it. */
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "SimpleHomes name lookup");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * Where a name is looked up. The server's profile repository in production, a stub in tests.
     */
    public interface Repository {
        Answer find(String name);
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
    private final BooleanSupplier onServerThread;
    private final long[] recent = new long[SERVER_LOOKUPS];
    private int next;

    public NameLookup(
            Repository repository,
            Executor worker,
            Executor serverThread,
            BooleanSupplier onServerThread) {
        this.repository = repository;
        this.worker = worker;
        this.serverThread = serverThread;
        this.onServerThread = onServerThread;
    }

    public static NameLookup of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, NameLookup::forServer);
        }
    }

    /**
     * The lookup for {@code server}. It reaches the server through a weak reference, so the map's
     * entry can be collected once the server is gone: a value that held its own key would keep
     * every integrated server alive.
     */
    private static NameLookup forServer(MinecraftServer server) {
        WeakReference<MinecraftServer> reference = new WeakReference<>(server);
        return new NameLookup(
                name -> {
                    MinecraftServer current = reference.get();
                    return current == null ? new Answer(Status.FAILED, null) : find(current, name);
                },
                EXECUTOR,
                runnable -> {
                    MinecraftServer current = reference.get();
                    if (current == null) return;
                    try {
                        current.executeIfPossible(runnable);
                    } catch (RejectedExecutionException stopping) {
                        // The server is shutting down; the answer has nowhere to go.
                    }
                },
                () -> {
                    MinecraftServer current = reference.get();
                    return current != null && current.isSameThread();
                });
    }

    /**
     * Asks the server's profile repository, which reports an unknown name and a failed request
     * differently: {@code findProfileByName} answers both with an empty result ({@code
     * MinecraftServicesProfileRepository.findProfileByName} catches {@code
     * MinecraftClientException}, which a timeout and an HTTP error both become), while {@code
     * findProfilesByNames} calls back with {@code ProfileNotFoundException} for a name Mojang does
     * not know and with the client exception for a request that failed.
     */
    private static Answer find(MinecraftServer server, String name) {
        Answer[] answer = new Answer[1];
        server.services()
                .profileRepository()
                .findProfilesByNames(
                        new String[] {name},
                        new ProfileLookupCallback() {
                            @Override
                            public void onProfileLookupSucceeded(String found, UUID id) {
                                answer[0] = new Answer(Status.FOUND, new NameAndId(id, found));
                            }

                            @Override
                            public void onProfileLookupFailed(String failed, Exception exception) {
                                answer[0] =
                                        new Answer(
                                                exception instanceof ProfileNotFoundException
                                                        ? Status.NOT_FOUND
                                                        : Status.FAILED,
                                                null);
                            }
                        });
        return answer[0] == null ? new Answer(Status.FAILED, null) : answer[0];
    }

    /**
     * Looks {@code name} up on the lookup thread and hands the answer to {@code callback} on the
     * server thread. A name that cannot belong to any account, and a lookup over the server's share
     * of Mojang's limit, are answered at once and never reach the network.
     */
    public void lookup(String name, Consumer<Answer> callback) {
        if (!StringUtil.isValidPlayerName(name)) {
            callback.accept(new Answer(Status.NOT_FOUND, null));
            return;
        }
        if (!reserve()) {
            callback.accept(new Answer(Status.TOO_MANY, null));
            return;
        }
        long started = System.nanoTime();
        worker.execute(
                () -> {
                    Answer answer;
                    try {
                        answer = repository.find(name);
                    } catch (Exception | Error exception) {
                        SimpleHomes.LOGGER.warn(
                                "Could not look up the player name {}", name, exception);
                        answer = new Answer(Status.FAILED, null);
                    }
                    Answer delivered = answer;
                    serverThread.execute(
                            () -> {
                                // During shutdown the server runs a task inline on the calling
                                // thread (BlockableEventLoop.java:98-104), where the name cache and
                                // the saved data must not be written.
                                if (!onServerThread.getAsBoolean()) return;
                                if (System.nanoTime() - started > MAX_AGE) return;
                                callback.accept(delivered);
                            });
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
