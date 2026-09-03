package com.howl.uwtracker.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.howl.uwtracker.modules.ArtifactStorageClient;
import com.howl.uwtracker.modules.ReadableArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>Also the app-wide {@link ArtifactStorageClient}: {@link #readObject}/{@link #openObject} serve
 * the registry-driven module artifacts (see {@code com.howl.uwtracker.modules}) from the same bucket
 * and the same lazy {@link Storage} handle. The SCTracker-specific {@link #fetch()} is unchanged.
 */
@Component
@ConditionalOnProperty(prefix = "plugin.storage", name = "bucket")
public class GcsPluginStorageClient implements PluginStorageClient, ArtifactStorageClient {

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

    @Override
    public Optional<byte[]> readObject(String objectPath) {
        try {
            return Optional.of(storage().readAllBytes(BlobId.of(props.bucket(), objectPath)));
        } catch (RuntimeException e) {
            log.warn("could not read gs://{}/{} — treating as absent", props.bucket(), objectPath, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<ReadableArtifact> openObject(String objectPath) {
        try {
            Blob blob = storage().get(BlobId.of(props.bucket(), objectPath));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            long size = blob.getSize() == null ? ReadableArtifact.UNKNOWN_SIZE : blob.getSize();
            InputStream stream = Channels.newInputStream(blob.reader());
            return Optional.of(new ReadableArtifact(stream, size));
        } catch (RuntimeException e) {
            log.warn("could not open gs://{}/{} — treating as absent", props.bucket(), objectPath, e);
            return Optional.empty();
        }
    }

    @Override
    public List<String> listSubdirectories(String prefix) {
        try {
            List<String> dirs = new ArrayList<>();
            for (Blob blob : storage().list(props.bucket(),
                    Storage.BlobListOption.prefix(prefix),
                    Storage.BlobListOption.currentDirectory()).iterateAll()) {
                String name = blob.getName(); // e.g. "plugins/SCTracker/"
                if (name.endsWith("/") && name.length() > prefix.length()) {
                    dirs.add(name.substring(prefix.length(), name.length() - 1));
                }
            }
            return dirs;
        } catch (RuntimeException e) {
            log.warn("could not list gs://{}/{} — treating as empty", props.bucket(), prefix, e);
            return List.of();
        }
    }

    @Override
    public boolean objectExists(String objectPath) {
        try {
            Blob blob = storage().get(BlobId.of(props.bucket(), objectPath));
            return blob != null && blob.exists();
        } catch (RuntimeException e) {
            log.warn("could not stat gs://{}/{} — treating as absent", props.bucket(), objectPath, e);
            return false;
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
