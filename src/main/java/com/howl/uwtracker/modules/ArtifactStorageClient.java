package com.howl.uwtracker.modules;

import java.util.Optional;

/**
 * Reads arbitrary artifact objects from the storage bucket by full object path — the
 * registry-driven counterpart to {@link com.howl.uwtracker.plugin.PluginStorageClient}, which only
 * knows the one hardcoded SCTracker object pair. The prod implementation is
 * {@link com.howl.uwtracker.plugin.GcsPluginStorageClient} (it implements both interfaces); tests
 * supply a fake.
 *
 * <p>Neither method throws — a missing object, an auth failure or a transport error all come back as
 * {@link Optional#empty()}.
 */
public interface ArtifactStorageClient {

    /** Whole-object read, for small objects such as manifest JSON. */
    Optional<byte[]> readObject(String objectPath);

    /** Streaming handle for a (possibly large) artifact download. The caller closes {@code stream()}. */
    Optional<ReadableArtifact> openObject(String objectPath);
}
