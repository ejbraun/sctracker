package com.howl.uwtracker.plugin;

import com.howl.uwtracker.domain.PluginDllVersion;
import com.howl.uwtracker.repository.PluginDllVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Keeps the singleton {@code plugin_dll_version} row (id={@link PluginDllVersion#SINGLETON_ID}) in
 * step with the plugin build currently in the storage bucket. Whenever
 * {@link PluginArtifactCache} pulls in a manifest with a new {@code sha256} — the first successful
 * fetch, or any later refresh that sees a changed build — it publishes a
 * {@link PluginDllChangedEvent}, and this records a fresh {@code detected_at}. That timestamp is
 * what the website's "new plugin version available" banner compares each person's
 * {@code last_plugin_download_at} against (see {@link PluginVersionService}).
 *
 * <p>Unlike the previous startup-only classpath hash, this can now fire mid-run: a plugin published
 * to the bucket is picked up within the cache TTL without a backend redeploy.
 *
 * <p>The event is published from a different bean and delivered through Spring's event
 * multicaster, so this {@code @EventListener} is invoked through the proxy and its
 * {@code @Transactional} boundary applies. Not {@code @TransactionalEventListener}: the cache
 * refresh runs on a plain read path with no surrounding transaction to bind an AFTER_COMMIT phase to.
 */
@Component
public class PluginDllVersionInitializer {

    private static final Logger log = LoggerFactory.getLogger(PluginDllVersionInitializer.class);

    private final PluginDllVersionRepository pluginDllVersionRepository;

    public PluginDllVersionInitializer(PluginDllVersionRepository pluginDllVersionRepository) {
        this.pluginDllVersionRepository = pluginDllVersionRepository;
    }

    @EventListener
    @Transactional
    public void onPluginDllChanged(PluginDllChangedEvent event) {
        Optional<PluginDllVersion> existing = pluginDllVersionRepository.findById(PluginDllVersion.SINGLETON_ID);
        if (existing.isEmpty()) {
            pluginDllVersionRepository.save(new PluginDllVersion(event.sha256(), event.detectedAt()));
            log.info("recorded initial SCTracker.dll version (sha256={})", event.sha256());
        } else if (!existing.get().getContentHash().equalsIgnoreCase(event.sha256())) {
            PluginDllVersion version = existing.get();
            version.setContentHash(event.sha256());
            version.setDetectedAt(event.detectedAt());
            log.info("SCTracker.dll changed — new version detected (sha256={})", event.sha256());
        }
        // else: same build as the row already has, nothing to do.
    }
}
