package com.howl.uwtracker.admin.dto;

/**
 * A {@code plugins/<Folder>/} directory found in the storage bucket that has a {@code <Folder>.dll}
 * but no {@code modules} row yet — a candidate for {@code POST /api/admin/modules}. The frontend
 * pre-fills the create form from these fields; the admin still picks the display name and, crucially,
 * whether it's public (nothing in the bucket says).
 */
public record DiscoveredModuleResponse(
        String folderName,
        String suggestedKey,
        String suggestedDisplayName,
        String bucketPrefix,
        String artifactObject,
        String manifestObject,
        boolean hasManifest) {
}
