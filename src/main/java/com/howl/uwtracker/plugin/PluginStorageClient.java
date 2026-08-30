package com.howl.uwtracker.plugin;

import java.util.Optional;

/**
 * Reads the SCTracker plugin artifacts (dll + manifest) from wherever they're published. The prod
 * implementation ({@link GcsPluginStorageClient}) pulls them from a private GCS bucket; tests
 * supply a fake.
 *
 * <p>{@link #fetch()} never throws — a network error, a missing object, or malformed JSON all come
 * back as {@link Optional#empty()}, meaning "no usable artifacts this cycle"; {@link PluginArtifactCache}
 * then keeps serving whatever it already had (or nothing).
 */
public interface PluginStorageClient {

    Optional<PluginArtifacts> fetch();
}
