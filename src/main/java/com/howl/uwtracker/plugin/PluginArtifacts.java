package com.howl.uwtracker.plugin;

/**
 * One consistent fetch of the plugin: the parsed manifest and the raw dll bytes it describes,
 * always retrieved and cached together so the version/hash the manifest declares and the bytes
 * served at {@code GET /SCTracker.dll} can't drift apart. Produced by {@link PluginStorageClient},
 * held by {@link PluginArtifactCache}.
 */
public record PluginArtifacts(PluginVersionMetadata manifest, byte[] dll) {
}
