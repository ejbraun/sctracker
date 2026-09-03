package com.howl.uwtracker.plugin;

import com.howl.uwtracker.modules.ArtifactStorageClient;
import com.howl.uwtracker.modules.ReadableArtifact;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayInputStream;
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
 * <p>For SCTracker ({@link PluginStorageClient#fetch()}) it returns a fixed manifest at version 10
 * (matching {@code CURRENT_PLUGIN_VERSION} in the upload tests, so {@code X-Plugin-Version: "1"}
 * correctly 426s) plus small fixed dll bytes whose real SHA-256 is the manifest {@code sha256}, so
 * {@link PluginArtifactCache}'s self-check passes and it publishes a {@link PluginDllChangedEvent}
 * at prime — what populates {@code plugin_dll_version} for {@code PluginIntegrationTest}.
 *
 * <p>As {@link ArtifactStorageClient} (for the registry-driven module artifacts) it answers any
 * {@code *.version.json} path with a synthetic manifest at {@link #FAKE_VERSION} and every other
 * path with {@link #fakeArtifactBytes(String)} — deterministic from the path so tests can assert on
 * the downloaded bytes.
 */
@TestConfiguration
public class FakePluginStorageConfig {

    public static final int FAKE_VERSION = 10;
    public static final byte[] FAKE_DLL = "fake-sctracker-dll".getBytes(StandardCharsets.UTF_8);
    public static final String FAKE_SHA256 = sha256Hex(FAKE_DLL);
    public static final Instant FAKE_COMPILED_AT = Instant.parse("2026-08-29T11:38:10Z");

    /** Bytes the fake store returns for any non-manifest object path. */
    public static byte[] fakeArtifactBytes(String objectPath) {
        return ("fake-artifact:" + objectPath).getBytes(StandardCharsets.UTF_8);
    }

    /** The {@code sha256} the synthetic manifest at {@code manifestObjectPath} advertises. */
    public static String fakeManifestSha256(String manifestObjectPath) {
        return sha256Hex(fakeArtifactBytes(siblingArtifactPath(manifestObjectPath)));
    }

    @Bean
    @Primary
    public FakeStorageClient fakePluginStorageClient() {
        return new FakeStorageClient();
    }

    /** Implements both storage contracts so one {@code @Primary} bean covers plugin + module reads. */
    public static final class FakeStorageClient implements PluginStorageClient, ArtifactStorageClient {

        @Override
        public Optional<PluginArtifacts> fetch() {
            return Optional.of(new PluginArtifacts(
                    new PluginVersionMetadata("SCTracker", FAKE_VERSION, FAKE_COMPILED_AT, FAKE_SHA256),
                    FAKE_DLL));
        }

        @Override
        public Optional<byte[]> readObject(String objectPath) {
            if (objectPath.endsWith(".version.json")) {
                return Optional.of(syntheticManifestJson(objectPath));
            }
            return Optional.of(fakeArtifactBytes(objectPath));
        }

        @Override
        public Optional<ReadableArtifact> openObject(String objectPath) {
            byte[] bytes = fakeArtifactBytes(objectPath);
            return Optional.of(new ReadableArtifact(new ByteArrayInputStream(bytes), bytes.length));
        }
    }

    private static byte[] syntheticManifestJson(String manifestObjectPath) {
        String name = manifestObjectPath.substring(manifestObjectPath.lastIndexOf('/') + 1)
                .replaceFirst("\\.version\\.json$", "");
        String json = "{\"name\":\"" + name + "\",\"version\":" + FAKE_VERSION
                + ",\"compiled_at\":\"" + FAKE_COMPILED_AT + "\",\"sha256\":\""
                + fakeManifestSha256(manifestObjectPath) + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /** {@code "plugins/Foo/Foo.version.json"} -> {@code "plugins/Foo/Foo.dll"}. */
    private static String siblingArtifactPath(String manifestObjectPath) {
        return manifestObjectPath.replaceFirst("\\.version\\.json$", "") + ".dll";
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
