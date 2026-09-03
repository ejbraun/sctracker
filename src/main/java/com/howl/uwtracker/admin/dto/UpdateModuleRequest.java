package com.howl.uwtracker.admin.dto;

/**
 * Body of {@code PATCH /api/admin/modules/{moduleKey}} — every field nullable, only the non-null
 * ones are applied. {@code module_key} is immutable and not accepted here.
 */
public record UpdateModuleRequest(
        String displayName,
        Boolean isPublic,
        Boolean enabled,
        String bucketPrefix,
        String artifactObject,
        String manifestObject,
        String contentType,
        Integer sortOrder) {
}
