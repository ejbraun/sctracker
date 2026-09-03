package com.howl.uwtracker.admin.dto;

/**
 * Body of {@code POST /api/admin/modules}. {@code isPublic} defaults to false, {@code sortOrder} to
 * 0, {@code contentType} to {@code application/octet-stream} when null/blank. {@code manifestObject}
 * is optional (an artifact with no manifest sidecar).
 */
public record CreateModuleRequest(
        String moduleKey,
        String displayName,
        Boolean isPublic,
        String bucketPrefix,
        String artifactObject,
        String manifestObject,
        String contentType,
        Integer sortOrder) {
}
