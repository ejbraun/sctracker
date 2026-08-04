package com.howl.uwtracker.loserboards.dto;

/**
 * Per-role, per-user fail attribution. {@code fails} only counts wipes where the objective the
 * party was wiping on (the {@code run_objectives} row with {@code status = 1}) is gated to this
 * role via {@code role_objectives} — a wipe doesn't count against a role that had nothing to do
 * with it. Resigns never count here; see {@link UserResignResponse}. {@code totalRuns} counts
 * every run this user played this role in, win or lose.
 */
public record RoleUserFailResponse(String role, String user, Long totalRuns, Long fails, Double percentage) {
}
