package com.howl.uwtracker.admin.dto;

import com.howl.uwtracker.domain.ModuleType;

/**
 * Body of {@code POST /api/admin/modules}. {@code type} defaults to {@code plugin}, {@code isPublic}
 * to false, {@code sortOrder} to 0, {@code contentType} to {@code application/octet-stream} when
 * null/blank. {@code manifestObject} and {@code patchNotesObject} are both optional (an artifact
 * need not have a manifest sidecar or patch notes).
 */
public record CreateModuleRequest(
        String moduleKey,
        String displayName,
        ModuleType type,
        Boolean isPublic,
        String bucketPrefix,
        String artifactObject,
        String manifestObject,
        String contentType,
        Integer sortOrder,
        String patchNotesObject) {
}
