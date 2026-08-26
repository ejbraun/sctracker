package com.howl.uwtracker.plugin;

import com.howl.uwtracker.plugin.dto.PluginVersionResponse;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no machine key, no session) — just "what's the latest build," non-sensitive. Top-level,
 * not under /api/**, same convention as every other plugin-facing endpoint (/upload-run,
 * /report-run-failure, /can-report-run-failure, /report-run-mvp). The plugin calls this proactively on load to
 * compare against its own compiled-in version and self-disable/warn if behind; the 426 a stale
 * client gets back from its actual API calls (see MachineKeyAuthenticationService) is the backstop
 * for when that proactive check didn't happen or is out of date.
 */
@RestController
public class PluginVersionController {

    private final PluginVersionMetadataLoader pluginVersionMetadataLoader;

    public PluginVersionController(PluginVersionMetadataLoader pluginVersionMetadataLoader) {
        this.pluginVersionMetadataLoader = pluginVersionMetadataLoader;
    }

    @GetMapping("/plugin-version")
    public ResponseEntity<PluginVersionResponse> latestVersion() {
        PluginVersionMetadata current = pluginVersionMetadataLoader.getCurrent();
        if (current == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "plugin version metadata unavailable");
        }
        return ResponseEntity.ok(PluginVersionResponse.from(current));
    }
}
