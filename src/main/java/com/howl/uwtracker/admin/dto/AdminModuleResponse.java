package com.howl.uwtracker.admin.dto;

import com.howl.uwtracker.domain.Module;

import java.time.Instant;

/** Full registry row for the admin "Modules" page — {@code current_*} fields are read-only (set by the manifest cache). */
public record AdminModuleResponse(
        Long id,
        String moduleKey,
        String displayName,
        boolean isPublic,
        boolean enabled,
        String bucketPrefix,
        String artifactObject,
        String manifestObject,
        String contentType,
        Integer currentVersion,
        String currentSha256,
        Instant versionDetectedAt,
        int sortOrder) {

    public static AdminModuleResponse from(Module m) {
        return new AdminModuleResponse(m.getId(), m.getModuleKey(), m.getDisplayName(), m.isPublicAccess(),
                m.isEnabled(), m.getBucketPrefix(), m.getArtifactObject(), m.getManifestObject(), m.getContentType(),
                m.getCurrentVersion(), m.getCurrentSha256(), m.getVersionDetectedAt(), m.getSortOrder());
    }
}
