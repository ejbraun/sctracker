package com.howl.uwtracker.leaderboards.dto;

import java.time.Instant;
import java.util.List;

/**
 * {@code startMs}/{@code doneMs} are steady-clock-relative offsets from the run's start, not
 * absolute timestamps — same caveat as {@code RunDetail.instance_start_ms} (specs/backend/06).
 * {@code participants} is the subset of that run's party whose role is gated in for this objective
 * (specs/backend/05 "role-gated"), i.e. who actually earned this section time — not the whole party.
 */
public record SectionEntryResponse(Long runId, Long durationMs, Instant utcStart, Long startMs, Long doneMs,
                                    List<ParticipantSummary> participants) {
}
