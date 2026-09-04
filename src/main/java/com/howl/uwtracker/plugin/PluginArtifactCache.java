package com.howl.uwtracker.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory hold of the SCTracker plugin artifacts (manifest + dll bytes) fetched from
 * {@link PluginStorageClient}, refreshed lazily: a read that finds the cache older than
 * {@link PluginStorageProperties#cacheTtl()} triggers a re-fetch, which resets the clock for
 * another TTL. A single-flight guard means concurrent readers past the TTL don't stampede the
 * bucket — only one refreshes, the rest serve whatever is currently held.
 *
 * <p>Everything fails open, matching the pre-GCS behaviour: no storage client configured (blank
 * bucket) → cache stays empty forever; a fetch error → keep serving the last good copy (or nothing
 * if there's never been one). Callers see {@code null} from {@link #getManifest()} /
 * {@link #getDll()} when nothing has ever loaded, and translate that to a 503 / fail-open as before.
 *
 * <p>The classpath-resource read that {@code PluginVersionMetadataLoader} used to do at startup is
 * replaced by {@link #prime()} here.
 */
@Component
public class PluginArtifactCache {

    private static final Logger log = LoggerFactory.getLogger(PluginArtifactCache.class);
    // Floor between fetch attempts while the cache is still empty, so a burst of first requests
    // before any success doesn't hit GCS on every single call.
    private static final Duration EMPTY_RETRY_FLOOR = Duration.ofSeconds(60);

    private final ObjectProvider<PluginStorageClient> storageClient;
    private final PluginStorageProperties props;

    private volatile PluginVersionMetadata manifest;
    private volatile byte[] dll;
    private volatile Instant fetchedAt;      // last successful fetch; null until one succeeds
    private volatile Instant lastAttemptAt;  // any attempt, success or not
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public PluginArtifactCache(ObjectProvider<PluginStorageClient> storageClient,
                                PluginStorageProperties props) {
        this.storageClient = storageClient;
        this.props = props;
    }

    /** Warm the cache once the context is up, so the first request isn't the one that pays for the fetch. */
    @EventListener(ApplicationReadyEvent.class)
    public void prime() {
        if (storageClient.getIfAvailable() == null) {
            log.info("plugin storage disabled: no bucket configured — /plugin-version and /SCTracker.dll "
                    + "will 503 and plugin-version enforcement is off");
            return;
        }
        try {
            refreshIfStale();
        } catch (RuntimeException e) {
            // Best-effort: a bucket that's unreachable at boot must not stop the app coming up. The
            // lazy path retries on the next request.
            log.warn("initial plugin artifact fetch failed — will retry on demand", e);
        }
        if (manifest != null) {
            log.info("primed plugin artifact cache (version={}, sha256={})", manifest.version(), manifest.sha256());
        }
    }

    public PluginVersionMetadata getManifest() {
        refreshIfStale();
        return manifest;
    }

    public byte[] getDll() {
        refreshIfStale();
        return dll;
    }

    private void refreshIfStale() {
        Instant now = Instant.now();
        if (fetchedAt != null && Duration.between(fetchedAt, now).compareTo(props.cacheTtl()) <= 0) {
            return; // fresh
        }
        if (fetchedAt == null && lastAttemptAt != null
                && Duration.between(lastAttemptAt, now).compareTo(EMPTY_RETRY_FLOOR) < 0) {
            return; // empty and we tried very recently — back off
        }
        PluginStorageClient client = storageClient.getIfAvailable();
        if (client == null) {
            return; // storage disabled — stay empty
        }
        if (!refreshing.compareAndSet(false, true)) {
            return; // another thread is on it; serve current state
        }
        try {
            // Re-check now that we hold the flag.
            if (fetchedAt != null && Duration.between(fetchedAt, Instant.now()).compareTo(props.cacheTtl()) <= 0) {
                return;
            }
            lastAttemptAt = Instant.now();
            Optional<PluginArtifacts> fetched = client.fetch();
            if (fetched.isEmpty()) {
                if (manifest != null) {
                    fetchedAt = Instant.now(); // serve the last good copy another TTL
                }
                return;
            }
            PluginArtifacts a = fetched.get();
            if (a.manifest().sha256() != null && !sha256Hex(a.dll()).equalsIgnoreCase(a.manifest().sha256())) {
                log.warn("fetched plugin manifest sha256 {} does not match the dll bytes — skipping this refresh",
                        a.manifest().sha256());
                return;
            }
            manifest = a.manifest();
            dll = a.dll();
            fetchedAt = Instant.now();
        } finally {
            refreshing.set(false);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always present
        }
    }
}
