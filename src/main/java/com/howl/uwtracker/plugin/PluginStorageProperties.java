package com.howl.uwtracker.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where {@link GcsPluginStorageClient} reads the SCTracker plugin artifacts from, and how long
 * {@link PluginArtifactCache} holds them. The first {@code @ConfigurationProperties} class in the
 * app — registered via {@code @EnableConfigurationProperties} on {@code Application}.
 *
 * <p>In prod the bucket arrives as the Cloud Run env var {@code PLUGIN_STORAGE_BUCKET} (Spring
 * relaxed binding). A blank {@code bucket} disables plugin storage entirely: no
 * {@link GcsPluginStorageClient} bean, so the cache stays empty and {@code GET /plugin-version} /
 * {@code GET /SCTracker.dll} return 503 and plugin-version enforcement fails open — the expected
 * state for local dev and the e2e backend.
 */
@ConfigurationProperties("plugin.storage")
public record PluginStorageProperties(
        @DefaultValue("") String bucket,
        @DefaultValue("sctracker/SCTracker.version.json") String manifestObject,
        @DefaultValue("sctracker/SCTracker.dll") String dllObject,
        @DefaultValue("1h") Duration cacheTtl) {
}
