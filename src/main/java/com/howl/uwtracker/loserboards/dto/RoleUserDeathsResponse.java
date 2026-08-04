package com.howl.uwtracker.loserboards.dto;

/**
 * Per-role, per-user death toll. Unlike {@link RoleUserFailResponse}, this isn't gated by
 * {@code role_objectives} — a death is a directly-recorded fact about that participant in that
 * run, not something inferred from where the party wiped, so every role's own deaths count as-is.
 */
public record RoleUserDeathsResponse(String role, String user, Long totalRuns, Long deaths, Double avgDeaths) {
}
