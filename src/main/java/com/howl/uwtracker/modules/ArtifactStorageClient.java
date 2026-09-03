package com.howl.uwtracker.modules;

import java.util.List;
import java.util.Optional;

/**
 * Reads arbitrary artifact objects from the storage bucket by full object path — the
 * registry-driven counterpart to {@link com.howl.uwtracker.plugin.PluginStorageClient}, which only
 * knows the one hardcoded SCTracker object pair. The prod implementation is
 * {@link com.howl.uwtracker.plugin.GcsPluginStorageClient} (it implements both interfaces); tests
 * supply a fake.
 *
 * <p>Nothing here throws — a missing object, an auth failure or a transport error come back as
 * {@link Optional#empty()} / an empty list / {@code false}.
 */
public interface ArtifactStorageClient {

    /** Whole-object read, for small objects such as manifest JSON. */
    Optional<byte[]> readObject(String objectPath);

    /** Streaming handle for a (possibly large) artifact download. The caller closes {@code stream()}. */
    Optional<ReadableArtifact> openObject(String objectPath);

    /**
     * Immediate "sub-directory" names under {@code prefix} (which must end with {@code /}), each
     * returned without the parent prefix or the trailing slash. E.g. {@code listSubdirectories("plugins/")}
     * → {@code ["PP-Vanquish", "SCTracker"]}. Empty when storage is unreachable.
     */
    List<String> listSubdirectories(String prefix);

    /** Whether an object exists at {@code objectPath} (a cheap metadata check, no download). */
    boolean objectExists(String objectPath);
}
