package com.howl.uwtracker.loserboards.dto;

/**
 * Per-user resign stats — "Global fails", not attributable to a single role since resigning is a
 * group decision. {@code totalRuns} counts every run this user played (any role); {@code resigns}
 * counts only the ones with {@code end_reason = 'resign'}.
 */
public record UserResignResponse(String user, Long totalRuns, Long resigns, Double percentage) {
}
