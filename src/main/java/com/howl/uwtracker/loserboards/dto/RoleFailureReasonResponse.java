package com.howl.uwtracker.loserboards.dto;

/**
 * Per-role, per-user count of how many times that role was flagged (via POST /report-run-failure)
 * as at fault for a run's failure on this map. {@code user} is the character who held that role in
 * the flagged run, {@code COALESCE(alias, raw_name)} same as every other loserboard/leaderboard row.
 */
public record RoleFailureReasonResponse(String role, String user, Long count) {
}
