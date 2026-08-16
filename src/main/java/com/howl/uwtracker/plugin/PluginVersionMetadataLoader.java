package com.howl.uwtracker.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howl.uwtracker.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads static/SCTracker.version.json once at startup and holds it in memory for the app's
 * lifetime — unlike {@link PluginDllVersionInitializer}'s content-hash tracking (which persists
 * across deployments to detect *whether* the dll changed), this file directly and authoritatively
 * states the current version, so there's nothing to diff against a previous boot and no need for
 * DB persistence.
 *
 * <p>This is the functional enforcement gate (via {@link #requireCurrentVersion}, called from
 * {@link com.howl.uwtracker.web.MachineKeyAuthenticationService}) for every machine-key-
 * authenticated endpoint, and the source of truth {@code GET /plugin-version} serves so the plugin
 * can proactively check itself. {@link PluginDllVersionInitializer}'s hash tracking remains
 * separate, only powering the website's human-facing "new version available" banner.
 */
@Component
public class PluginVersionMetadataLoader {

    private static final Logger log = LoggerFactory.getLogger(PluginVersionMetadataLoader.class);
    private static final String METADATA_CLASSPATH_LOCATION = "static/SCTracker.version.json";

    private final ObjectMapper objectMapper;
    private volatile PluginVersionMetadata current;

    public PluginVersionMetadataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadCurrentVersion() {
        try (InputStream in = new ClassPathResource(METADATA_CLASSPATH_LOCATION).getInputStream()) {
            current = objectMapper.readValue(in, PluginVersionMetadata.class);
            log.info("loaded plugin version metadata (version={}, compiledAt={})", current.version(), current.compiledAt());
        } catch (IOException e) {
            // Deliberately non-fatal, same rationale as PluginDllVersionInitializer: a missing/
            // malformed metadata file shouldn't block the whole app from starting. With current
            // left null, requireCurrentVersion below has nothing to enforce against and no-ops.
            log.warn("could not load {} — plugin version enforcement will be disabled", METADATA_CLASSPATH_LOCATION, e);
        }
    }

    public PluginVersionMetadata getCurrent() {
        return current;
    }

    /**
     * Throws 426 Upgrade Required if {@code clientVersion} is missing or below the current known
     * version — a distinct, unique status the plugin can detect specifically (rather than a generic
     * 400/401) and react to with its own "please update" UI, not just a silently-logged failure.
     * No-ops if {@link #current} couldn't be loaded (see {@link #loadCurrentVersion}) — enforcement
     * fails open rather than locking out every client over a backend deployment mistake.
     */
    public void requireCurrentVersion(Integer clientVersion) {
        PluginVersionMetadata latest = current;
        if (latest == null) {
            return;
        }
        if (clientVersion == null || clientVersion < latest.version()) {
            throw new ApiException(HttpStatus.UPGRADE_REQUIRED,
                    "plugin version " + clientVersion + " is outdated; latest is " + latest.version());
        }
    }
}
