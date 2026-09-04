package com.howl.uwtracker.leaderboards.dto;

/**
 * Per-user count of how many times that character earned the MVP award (via the plugin's post-run
 * MVP popup) on a role-less {@code (map, party_size)} config — the character-name counterpart of
 * {@link RoleMvpAwardResponse} for a config with no role model (see
 * specs/features/fow-and-party-size.md §9.6). No {@code role} dimension: a role-less config has
 * none to group by.
 */
public record CharacterMvpAwardResponse(String user, Long totalRuns, Long awards, Double avgAwards) {
}
