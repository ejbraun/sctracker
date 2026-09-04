package com.howl.uwtracker.admin.dto;

/**
 * Response of {@code POST /api/admin/signup-links}. The raw {@code token} is present here only —
 * never stored (only its hash is), never returned again. The frontend builds the shareable URL as
 * {@code <origin>/signup?invite=<token>}.
 */
public record GeneratedSignupLinkResponse(Long id, String token, String label, Integer maxUses) {
}
