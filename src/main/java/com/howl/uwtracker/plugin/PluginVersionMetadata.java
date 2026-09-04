package com.howl.uwtracker.plugin;

import java.time.Instant;

/**
 * The plugin manifest ({@code SCTracker.version.json}), fetched from the plugin storage bucket
 * alongside {@code SCTracker.dll} — see {@link PluginArtifactCache}. Written by the plugin build
 * (GWToolboxpp's {@code plugins/Base/write-plugin-manifest.cmake}) on every build, so
 * {@code compiledAt} and {@code sha256} are always current for the dll they sit next to.
 *
 * <p>{@code name} and {@code sha256} are tolerated-absent (older manifests) — a null {@code sha256}
 * just means {@link PluginArtifactCache} can't self-check the dll bytes against the manifest.
 * {@code version} drives the {@code X-Plugin-Version} enforcement gate
 * ({@link PluginVersionMetadataLoader#requireCurrentVersion}) and the website's "new plugin version
 * available" banner ({@link PluginVersionService#isOutdated}).
 */
public record PluginVersionMetadata(String name, int version, Instant compiledAt, String sha256) {
}
