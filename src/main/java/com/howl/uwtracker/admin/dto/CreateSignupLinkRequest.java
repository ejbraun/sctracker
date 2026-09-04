package com.howl.uwtracker.admin.dto;

/**
 * Body of {@code POST /api/admin/signup-links} — wire: {@code {"label": "...", "max_uses": 10}}.
 * Both fields optional: {@code label} defaults to null, {@code maxUses} to 10 (bound 1–100).
 */
public record CreateSignupLinkRequest(String label, Integer maxUses) {
}
