package com.howl.uwtracker.plugin;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Stands in for {@link GcsPluginStorageClient} in integration tests — no GCS, no ADC, no bucket.
 * Imported by {@code AbstractIntegrationTest}, so every integration test gets it with no per-class
 * wiring. {@code @Primary} so it wins even if a test ever sets {@code plugin.storage.bucket}.
 *
 * <p>Returns a fixed manifest at version 10 (matching {@code CURRENT_PLUGIN_VERSION} in the upload
 * tests and making {@code X-Plugin-Version: "1"} correctly 426) plus small fixed dll bytes whose
 * real SHA-256 is used as the manifest {@code sha256}, so {@link PluginArtifactCache}'s
 * bytes-vs-manifest self-check passes and it publishes a {@link PluginDllChangedEvent} at prime —
 * which is what populates {@code plugin_dll_version} for {@code PluginIntegrationTest}.
 */
@TestConfiguration
public class FakePluginStorageConfig {

    public static final int FAKE_VERSION = 10;
    public static final byte[] FAKE_DLL = "fake-sctracker-dll".getBytes(StandardCharsets.UTF_8);
    public static final String FAKE_SHA256 = sha256Hex(FAKE_DLL);
    public static final Instant FAKE_COMPILED_AT = Instant.parse("2026-08-29T11:38:10Z");

    @Bean
    @Primary
    public PluginStorageClient fakePluginStorageClient() {
        PluginArtifacts artifacts = new PluginArtifacts(
                new PluginVersionMetadata("SCTracker", FAKE_VERSION, FAKE_COMPILED_AT, FAKE_SHA256),
                FAKE_DLL);
        return () -> Optional.of(artifacts);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
