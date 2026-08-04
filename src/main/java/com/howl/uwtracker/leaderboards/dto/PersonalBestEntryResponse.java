package com.howl.uwtracker.leaderboards.dto;

import java.time.Instant;
import java.util.List;

/** Same shape as {@link LeaderboardEntryResponse} — the "Yours" table matches "Global"'s schema. */
public record PersonalBestEntryResponse(Long runId, Long durationMs, Instant utcStart, List<ParticipantSummary> participants) {
}
