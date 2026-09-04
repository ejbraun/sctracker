package com.howl.uwtracker.loserboards.dto;

/**
 * Per-user fail count out of every run that user has played on a role-less {@code (map,
 * party_size)} config (not just the flagged ones) — the character-name counterpart of
 * {@link RoleFailureReasonResponse} for a config with no role model (see
 * specs/features/fow-and-party-size.md §9.6). No {@code role} dimension: a role-less config has
 * none to group by. {@code user} is {@code COALESCE(alias, raw_name)}, same as every other
 * loserboard/leaderboard row.
 */
public record CharacterFailureReasonResponse(String user, Long totalRuns, Long fails, Double avgFails) {
}
