package com.howl.uwtracker.admin.dto;

import com.howl.uwtracker.domain.ModuleType;

/**
 * A {@code <prefix>/<Folder>/} directory found in the storage bucket that has a recognisable
 * artifact ({@code <Folder>.dll} / {@code .zip} / {@code .exe}) but no {@code modules} row yet — a
 * candidate for {@code POST /api/admin/modules}. The frontend pre-fills the create form from these
 * fields; the admin still picks the display name and, crucially, whether it's public (nothing in the
 * bucket says). {@code suggestedType} follows the prefix the folder sits under — {@code plugin} for
 * {@code plugins/}, {@code module} for {@code launcher/}. {@code patchNotesObject} mirrors
 * {@code manifestObject}: derived as {@code <Folder>.patch.txt} and only non-null when it exists.
 */
public record DiscoveredModuleResponse(
        String folderName,
        String suggestedKey,
        String suggestedDisplayName,
        ModuleType suggestedType,
        String bucketPrefix,
        String artifactObject,
        String manifestObject,
        boolean hasManifest,
        String patchNotesObject,
        boolean hasPatchNotes) {
}
