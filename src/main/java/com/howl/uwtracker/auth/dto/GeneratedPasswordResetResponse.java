package com.howl.uwtracker.auth.dto;

import java.time.Instant;

/**
 * Response of {@code POST /api/admin/users/{id}/password-reset}. The raw {@code token} is present
 * here only — never stored (only its hash is), never returned again. The frontend builds the
 * shareable URL as {@code <origin>/reset-password?token=<token>}.
 */
public record GeneratedPasswordResetResponse(String token, Instant expiresAt) {
}
