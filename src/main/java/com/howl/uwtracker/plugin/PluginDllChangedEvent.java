package com.howl.uwtracker.plugin;

import java.time.Instant;

/**
 * Published by {@link PluginArtifactCache} whenever a refresh brings in a manifest whose
 * {@code sha256} differs from the one currently cached (including the first successful fetch).
 * {@link PluginDllVersionInitializer} listens for it and advances the {@code plugin_dll_version}
 * row, which is what the website's "new plugin version available" banner compares against.
 */
public record PluginDllChangedEvent(String sha256, Instant detectedAt) {
}
