package com.howl.uwtracker.leaderboards.dto;

import java.time.Instant;

/**
 * One user's single best-ever consecutive-run streak on a map — reused by both Leaderboards
 * (longest completed streak) and Loserboards (longest resign/wipe streak), same cross-package
 * reuse as {@link LeaderboardEntryResponse} being reused by LoserboardService.worst.
 */
public record UserStreakResponse(String user, Long streak, Instant streakStart, Instant streakEnd) {
}
