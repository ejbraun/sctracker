package com.howl.uwtracker.admin.dto;

import java.time.Instant;

/**
 * One row of the per-user module checklist ({@code GET /api/admin/users/{personId}/modules}):
 * every enabled module with whether this user currently has a grant. Public modules show as
 * {@code granted = false} but the UI renders them as "always available".
 */
public record AdminUserModuleResponse(
        String moduleKey,
        String displayName,
        boolean isPublic,
        boolean granted,
        Instant grantedAt,
        Long grantedBy) {
}
