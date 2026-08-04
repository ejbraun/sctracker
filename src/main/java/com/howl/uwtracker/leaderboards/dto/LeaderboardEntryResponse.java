package com.howl.uwtracker.leaderboards.dto;

import java.time.Instant;
import java.util.List;

public record LeaderboardEntryResponse(Long runId, Long durationMs, Instant utcStart, List<ParticipantSummary> participants) {
}
