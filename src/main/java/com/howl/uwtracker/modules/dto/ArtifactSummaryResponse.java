package com.howl.uwtracker.modules.dto;

import com.howl.uwtracker.domain.ModuleType;

import java.time.Instant;

/**
 * One row of {@code GET /artifacts}. {@code version} / {@code compiledAt} / {@code sha256} come from
 * the artifact's manifest (live for {@code sctracker}, the cached {@code current_*} columns
 * otherwise) and are {@code null} until it has been seen at least once. {@code downloadUrl} is
 * app-relative — the caller composes it with its configured backend base URL.
 */
public record ArtifactSummaryResponse(
        String key,
        String displayName,
        ModuleType type,
        boolean isPublic,
        Integer version,
        Instant compiledAt,
        String sha256,
        String downloadUrl) {
}
