package com.howl.uwtracker.loserboards.dto;

/**
 * Per-role, per-user fail count out of every run that user has played that role in on this map
 * (not just the flagged ones) — same shape as {@code RoleUserDeathsResponse}, so a single blame in
 * a single run doesn't outrank someone blamed less often but far more frequently relative to their
 * total runs in that role. {@code user} is the character who held that role in each run,
 * {@code COALESCE(alias, raw_name)} same as every other loserboard/leaderboard row.
 */
public record RoleFailureReasonResponse(String role, String user, Long totalRuns, Long fails, Double avgFails) {
}
