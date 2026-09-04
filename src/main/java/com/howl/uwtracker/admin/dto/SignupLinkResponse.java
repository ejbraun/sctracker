package com.howl.uwtracker.admin.dto;

import com.howl.uwtracker.domain.SignupLink;

import java.time.Instant;

/**
 * Row shape for the admin "Signup Links" table ({@code GET /api/admin/signup-links}). Never carries
 * the token or its hash. Status is derived on the frontend: revoked if {@code revokedAt} is set,
 * else "used up" once {@code useCount >= maxUses}, else active.
 */
public record SignupLinkResponse(Long id, String label, int maxUses, int useCount,
                                 Instant createdAt, Instant revokedAt) {

    public static SignupLinkResponse from(SignupLink link) {
        return new SignupLinkResponse(link.getId(), link.getLabel(), link.getMaxUses(),
                link.getUseCount(), link.getCreatedAt(), link.getRevokedAt());
    }
}
