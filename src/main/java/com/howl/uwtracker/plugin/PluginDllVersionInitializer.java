package com.howl.uwtracker.plugin;

import com.howl.uwtracker.domain.PluginDllVersion;
import com.howl.uwtracker.repository.PluginDllVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * On every app startup, hashes the deployed SCTracker.dll and compares it against the last-known
 * hash (see {@link PluginDllVersion} for why a content hash, not the file's on-disk last-modified
 * time). If the content changed — or this is the first boot ever — records a fresh "detected at"
 * timestamp, which is what the "new plugin version available" banner compares each person's
 * last_plugin_download_at against.
 *
 * <p>Runs on {@link ApplicationReadyEvent} rather than {@code @PostConstruct} so it fires strictly
 * after Liquibase has created plugin_dll_version — {@code @PostConstruct} on an unrelated bean
 * gives no such ordering guarantee against another bean's migrations.
 */
@Component
public class PluginDllVersionInitializer {

    private static final Logger log = LoggerFactory.getLogger(PluginDllVersionInitializer.class);
    private static final String DLL_CLASSPATH_LOCATION = "static/SCTracker.dll";

    private final PluginDllVersionRepository pluginDllVersionRepository;

    public PluginDllVersionInitializer(PluginDllVersionRepository pluginDllVersionRepository) {
        this.pluginDllVersionRepository = pluginDllVersionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recordCurrentDllVersion() {
        String hash;
        try {
            hash = sha256Hex();
        } catch (IOException | NoSuchAlgorithmException e) {
            // Deliberately non-fatal: a missing/unreadable dll shouldn't block the whole app from
            // starting. No row (or a stale one) just means the "new version available" check has
            // nothing newer to compare against, so the banner stays off.
            log.warn("could not hash {} — plugin update banner will stay disabled", DLL_CLASSPATH_LOCATION, e);
            return;
        }

        Optional<PluginDllVersion> existing = pluginDllVersionRepository.findById(PluginDllVersion.SINGLETON_ID);
        if (existing.isEmpty()) {
            pluginDllVersionRepository.save(new PluginDllVersion(hash, Instant.now()));
            log.info("recorded initial SCTracker.dll version (hash={})", hash);
        } else if (!existing.get().getContentHash().equals(hash)) {
            PluginDllVersion version = existing.get();
            version.setContentHash(hash);
            version.setDetectedAt(Instant.now());
            log.info("SCTracker.dll content changed — new version detected (hash={})", hash);
        }
        // else: same content as last boot, nothing to do.
    }

    private String sha256Hex() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new ClassPathResource(DLL_CLASSPATH_LOCATION).getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
