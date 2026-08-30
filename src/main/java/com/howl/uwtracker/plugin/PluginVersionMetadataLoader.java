package com.howl.uwtracker.plugin;

import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Thin policy façade over {@link PluginArtifactCache} for plugin-version checks. Kept as its own
 * bean (rather than folding into the cache) because {@link #requireCurrentVersion} is a policy
 * method that throws {@link ApiException}, and because {@code MachineKeyAuthenticationService} and
 * {@code PluginVersionController} already inject this — leaving these two signatures in place keeps
 * them untouched.
 *
 * <p>The manifest now comes from the plugin storage bucket via the cache (previously read once from
 * a bundled classpath resource at startup); {@link PluginArtifactCache#getManifest()} returns
 * {@code null} until a fetch succeeds, and {@link #requireCurrentVersion} fails open in that case,
 * exactly as before.
 */
@Component
public class PluginVersionMetadataLoader {

    private final PluginArtifactCache cache;

    public PluginVersionMetadataLoader(PluginArtifactCache cache) {
        this.cache = cache;
    }

    public PluginVersionMetadata getCurrent() {
        return cache.getManifest();
    }

    /**
     * Throws 426 Upgrade Required if {@code clientVersion} is missing or below the current known
     * version — a distinct, unique status the plugin can detect specifically (rather than a generic
     * 400/401) and react to with its own "please update" UI, not just a silently-logged failure.
     * No-ops if the manifest hasn't loaded (no bucket configured, or the bucket is unreachable) —
     * enforcement fails open rather than locking out every client over an infrastructure problem.
     */
    public void requireCurrentVersion(Integer clientVersion) {
        PluginVersionMetadata latest = cache.getManifest();
        if (latest == null) {
            return;
        }
        if (clientVersion == null || clientVersion < latest.version()) {
            throw new ApiException(HttpStatus.UPGRADE_REQUIRED,
                    "plugin version " + clientVersion + " is outdated; latest is " + latest.version());
        }
    }
}
