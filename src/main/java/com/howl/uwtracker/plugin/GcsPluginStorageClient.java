package com.howl.uwtracker.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Fetches the SCTracker plugin artifacts from the private GCS bucket named by
 * {@link PluginStorageProperties#bucket()}. Only registered when that property is non-blank
 * ({@code @ConditionalOnProperty}), so local dev / the e2e backend simply have no
 * {@link PluginStorageClient} bean and {@link PluginArtifactCache} stays empty.
 *
 * <p>Auth is ADC ({@link StorageOptions#getDefaultInstance()}): the Cloud Run runtime service
 * account in prod (which needs {@code roles/storage.objectViewer} on the bucket),
 * {@code GOOGLE_APPLICATION_CREDENTIALS} or {@code gcloud auth application-default login} locally.
 * The {@link Storage} handle is built lazily on first use and every failure — including "no ADC
 * available" — is swallowed into {@link Optional#empty()}, so a misconfigured environment degrades
 * to a 503 on {@code /SCTracker.dll} + {@code /plugin-version} rather than a failed startup.
 */
@Component
@ConditionalOnProperty(prefix = "plugin.storage", name = "bucket")
public class GcsPluginStorageClient implements PluginStorageClient {

    private static final Logger log = LoggerFactory.getLogger(GcsPluginStorageClient.class);

    private final PluginStorageProperties props;
    private final ObjectMapper objectMapper;
    private volatile Storage storage;

    public GcsPluginStorageClient(PluginStorageProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PluginArtifacts> fetch() {
        try {
            Storage gcs = storage();
            // Read the dll first, manifest second — a read racing a CI upload of the two objects then
            // gets (old-or-new dll, older-or-equal manifest), so the declared sha256 can lag the
            // bytes by a cycle but never points ahead of them.
            byte[] dll = gcs.readAllBytes(BlobId.of(props.bucket(), props.dllObject()));
            byte[] manifestJson = gcs.readAllBytes(BlobId.of(props.bucket(), props.manifestObject()));
            PluginVersionMetadata manifest = objectMapper.readValue(manifestJson, PluginVersionMetadata.class);
            return Optional.of(new PluginArtifacts(manifest, dll));
        } catch (RuntimeException | IOException e) {
            log.warn("could not fetch plugin artifacts from gs://{}/ ({}, {}) — cache keeps its current state",
                    props.bucket(), props.dllObject(), props.manifestObject(), e);
            return Optional.empty();
        }
    }

    private Storage storage() {
        Storage local = storage;
        if (local == null) {
            synchronized (this) {
                local = storage;
                if (local == null) {
                    storage = local = StorageOptions.getDefaultInstance().getService();
                }
            }
        }
        return local;
    }
}
