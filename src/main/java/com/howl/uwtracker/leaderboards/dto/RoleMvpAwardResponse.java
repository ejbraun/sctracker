package com.howl.uwtracker.leaderboards.dto;

/**
 * Per-role, per-user count of how many times that role earned the MVP award (via the plugin's
 * post-run MVP popup) on this map — the positive-side mirror of loserboards'
 * {@code RoleFailureReasonResponse}. {@code user} is whichever character held that role in each
 * awarded run.
 */
public record RoleMvpAwardResponse(String role, String user, Long totalRuns, Long awards, Double avgAwards) {
}
