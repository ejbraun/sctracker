package com.howl.uwtracker.modules.dto;

import java.util.List;

/**
 * Body of {@code GET /module-entitlements}: every module the calling machine key's person may use —
 * the public ones plus whatever's been granted. The launcher enables/downloads exactly this set.
 */
public record ModuleEntitlementsResponse(List<Entry> modules) {

    public record Entry(
            String key,
            String displayName,
            boolean isPublic,
            Integer version,
            String sha256,
            String downloadUrl) {
    }
}
