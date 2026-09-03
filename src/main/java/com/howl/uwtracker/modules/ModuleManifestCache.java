package com.howl.uwtracker.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.plugin.PluginStorageProperties;
import com.howl.uwtracker.plugin.PluginVersionMetadata;
import com.howl.uwtracker.repository.ModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-module cache of the artifact manifest ({@code {name, version, compiled_at, sha256}}) — the
 * metadata counterpart to {@link com.howl.uwtracker.plugin.PluginArtifactCache}, which additionally
 * holds the SCTracker dll bytes in memory. This one caches metadata only, on purpose: module
 * downloads stream straight from the bucket per request (see {@code ModuleDownloadController}) so
 * nothing large sits in RAM and the entitlement check always runs.
 *
 * <p>Lazy TTL-on-read per module id, single-flight per id, fail-open: a fetch error or an
 * unparseable manifest keeps the last good entry (or none). A changed {@code sha256} publishes a
 * {@link ModuleVersionChangedEvent}, which {@link ModuleVersionInitializer} writes back to the
 * module's {@code current_*} columns. SCTracker's {@code plugin_dll_version} singleton is untouched.
 *
 * <p>As with {@code PluginArtifactCache}, when no bucket is configured there is no
 * {@link ArtifactStorageClient} bean and the cache simply never populates.
 */
@Component
public class ModuleManifestCache {

    private static final Logger log = LoggerFactory.getLogger(ModuleManifestCache.class);
    // Floor between fetch attempts for a module that has never loaded, so a burst of first requests
    // doesn't hit the bucket on every call.
    private static final Duration EMPTY_RETRY_FLOOR = Duration.ofSeconds(60);

    /** {@code manifest}/{@code fetchedAt} null until a fetch succeeds; {@code lastAttemptAt} tracks any try. */
    private record Entry(PluginVersionMetadata manifest, Instant fetchedAt, Instant lastAttemptAt) {}

    private final ObjectProvider<ArtifactStorageClient> storageClient;
    private final PluginStorageProperties props;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final ModuleRepository moduleRepository;

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public ModuleManifestCache(ObjectProvider<ArtifactStorageClient> storageClient,
                               PluginStorageProperties props, ObjectMapper objectMapper,
                               ApplicationEventPublisher events, ModuleRepository moduleRepository) {
        this.storageClient = storageClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.events = events;
        this.moduleRepository = moduleRepository;
    }

    /** Warm every enabled module's manifest once the context is up, so no request pays the first fetch. */
    @EventListener(ApplicationReadyEvent.class)
    public void prime() {
        if (storageClient.getIfAvailable() == null) {
            log.info("module storage disabled: no bucket configured — module manifests will not be cached");
            return;
        }
        for (Module module : moduleRepository.findByEnabledTrueOrderBySortOrderAscModuleKeyAsc()) {
            try {
                refreshIfStale(module);
            } catch (RuntimeException e) {
                log.warn("initial manifest fetch failed for module {} — will retry on demand",
                        module.getModuleKey(), e);
            }
        }
    }

    /**
     * The module's current manifest, or empty when it declares no {@code manifest_object} or nothing
     * has loaded yet.
     */
    public Optional<PluginVersionMetadata> getManifest(Module module) {
        if (module.getManifestObject() == null || module.getManifestObject().isBlank()) {
            return Optional.empty();
        }
        refreshIfStale(module);
        Entry entry = entries.get(module.getId());
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.manifest());
    }

    /** Drop a module's cached manifest so the next read re-fetches — after an admin edits its object paths. */
    public void evict(Long moduleId) {
        entries.remove(moduleId);
        locks.remove(moduleId);
    }

    /** Test hook: forget every cached manifest. Production never needs this. */
    public void clear() {
        entries.clear();
        locks.clear();
    }

    private void refreshIfStale(Module module) {
        Long id = module.getId();
        Instant now = Instant.now();
        Entry current = entries.get(id);
        if (current != null && current.fetchedAt() != null
                && Duration.between(current.fetchedAt(), now).compareTo(props.moduleCacheTtl()) <= 0) {
            return; // fresh
        }
        if (current != null && current.fetchedAt() == null && current.lastAttemptAt() != null
                && Duration.between(current.lastAttemptAt(), now).compareTo(EMPTY_RETRY_FLOOR) < 0) {
            return; // never loaded, tried very recently — back off
        }
        ArtifactStorageClient client = storageClient.getIfAvailable();
        if (client == null) {
            return; // storage disabled
        }
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            Entry latest = entries.get(id);
            if (latest != null && latest.fetchedAt() != null
                    && Duration.between(latest.fetchedAt(), Instant.now()).compareTo(props.moduleCacheTtl()) <= 0) {
                return; // another caller just refreshed it
            }
            PluginVersionMetadata previous = latest == null ? null : latest.manifest();
            Instant fetchedAt = latest == null ? null : latest.fetchedAt();
            Instant attemptAt = Instant.now();

            Optional<byte[]> raw = client.readObject(module.getManifestObject());
            if (raw.isEmpty()) {
                entries.put(id, new Entry(previous, fetchedAt, attemptAt)); // keep last good
                return;
            }
            PluginVersionMetadata manifest;
            try {
                manifest = objectMapper.readValue(raw.get(), PluginVersionMetadata.class);
            } catch (Exception e) {
                log.warn("module {} manifest at {} is not parseable — ignoring this refresh",
                        module.getModuleKey(), module.getManifestObject(), e);
                entries.put(id, new Entry(previous, fetchedAt, attemptAt));
                return;
            }

            String previousSha = previous == null ? null : previous.sha256();
            Instant detectedAt = Instant.now();
            entries.put(id, new Entry(manifest, detectedAt, attemptAt));
            if (manifest.sha256() != null && !Objects.equals(previousSha, manifest.sha256())) {
                events.publishEvent(new ModuleVersionChangedEvent(id, manifest.version(), manifest.sha256(), detectedAt));
            }
        }
    }
}
