package com.howl.uwtracker.modules.dto;

import com.howl.uwtracker.domain.ModuleType;

import java.util.List;

/**
 * Body of {@code GET /module-entitlements}: every module the calling machine key's person may use —
 * the public ones plus whatever's been granted. GWRL enables/downloads exactly this set. Narrow it
 * with {@code ?type=plugin} / {@code ?type=module}.
 */
public record ModuleEntitlementsResponse(List<Entry> modules) {

    public record Entry(
            String key,
            String displayName,
            ModuleType type,
            boolean isPublic,
            Integer version,
            String sha256,
            String downloadUrl,
            /** Null when the module has no patch notes configured. */
            String patchNotesUrl) {
    }
}
