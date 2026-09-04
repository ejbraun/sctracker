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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for {@link GcsPluginStorageClient} in integration tests — no GCS, no ADC, no bucket.
 * Imported by {@code AbstractIntegrationTest}, so every integration test gets it with no per-class
 * wiring. {@code @Primary} so it wins even if a test ever sets {@code plugin.storage.bucket}.
 *
 * <p>For SCTracker ({@link PluginStorageClient#fetch()}) it returns a fixed manifest at version 10
 * (matching {@code CURRENT_PLUGIN_VERSION} in the upload tests, so {@code X-Plugin-Version: "1"}
 * correctly 426s) plus small fixed dll bytes whose real SHA-256 is the manifest {@code sha256}, so
 * {@link PluginArtifactCache}'s self-check passes — this is the current version
 * {@code PluginIntegrationTest} and {@code OutdatedPluginIntegrationTest} compare a client's
 * advertised {@code X-Plugin-Version} against.
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

    /**
     * Test-settable contents of {@code gs://<bucket>/plugins/} for the bucket-discovery path.
     * {@code PLUGIN_FOLDERS} have both {@code <F>.dll} and {@code <F>.version.json}; {@code EMPTY_DIRS}
     * are listed but have no dll. {@code AbstractIntegrationTest.cleanDatabase()} clears all three.
     */
    public static final Set<String> PLUGIN_FOLDERS = ConcurrentHashMap.newKeySet();
    public static final Set<String> EMPTY_DIRS = ConcurrentHashMap.newKeySet();

    /**
     * Test-settable contents of {@code gs://<bucket>/launcher/} — folder name to its artifact
     * filename (e.g. {@code "gwrl-install" -> "gwrl-install.zip"}), each also getting a sibling
     * {@code <folder>.version.json}. Exercises discovery's non-{@code .dll} extension probing and the
     * {@code launcher/} → {@code type: module} suggestion.
     */
    public static final Map<String, String> LAUNCHER_FOLDERS = new ConcurrentHashMap<>();

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

        @Override
        public List<String> listSubdirectories(String prefix) {
            if ("plugins/".equals(prefix)) {
                List<String> dirs = new ArrayList<>(PLUGIN_FOLDERS);
                dirs.addAll(EMPTY_DIRS);
                return dirs;
            }
            if ("launcher/".equals(prefix)) {
                return new ArrayList<>(LAUNCHER_FOLDERS.keySet());
            }
            return List.of();
        }

        @Override
        public boolean objectExists(String objectPath) {
            for (String folder : PLUGIN_FOLDERS) {
                if (objectPath.equals("plugins/" + folder + "/" + folder + ".dll")
                        || objectPath.equals("plugins/" + folder + "/" + folder + ".version.json")) {
                    return true;
                }
            }
            for (Map.Entry<String, String> entry : LAUNCHER_FOLDERS.entrySet()) {
                String base = "launcher/" + entry.getKey() + "/";
                if (objectPath.equals(base + entry.getValue())
                        || objectPath.equals(base + entry.getKey() + ".version.json")) {
                    return true;
                }
            }
            return false;
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
