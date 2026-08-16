package com.howl.uwtracker.plugin;

import java.time.Instant;

/**
 * Parsed from the static/SCTracker.version.json classpath resource, shipped alongside
 * SCTracker.dll — see {@link PluginVersionMetadataLoader}. Hand-maintained: bump {@code version}
 * (matching the plugin's own {@code kPluginVersion} constant) and {@code compiled_at} every time a
 * new dll build is checked in.
 */
public record PluginVersionMetadata(int version, Instant compiledAt) {
}
