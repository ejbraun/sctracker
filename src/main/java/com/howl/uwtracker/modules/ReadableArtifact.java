package com.howl.uwtracker.modules;

import java.io.InputStream;

/**
 * A streaming handle to one artifact object: its bytes and its total size ({@code -1} when the store
 * can't report one). {@code ModuleDownloadController} hands {@code stream} to Spring as an
 * {@code InputStreamResource}; Spring closes it after writing the response. The artifact's SHA-256
 * for the {@code ETag} comes from the module row ({@code current_sha256}), not from here, so it isn't
 * carried on this handle.
 */
public record ReadableArtifact(InputStream stream, long size) {

    public static final long UNKNOWN_SIZE = -1L;
}
