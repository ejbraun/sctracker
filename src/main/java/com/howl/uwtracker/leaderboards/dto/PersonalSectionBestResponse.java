package com.howl.uwtracker.leaderboards.dto;

import java.util.List;

/**
 * {@code startMs}/{@code doneMs} are steady-clock-relative offsets from the run's start, not
 * absolute timestamps — same as {@link SectionEntryResponse}'s. {@code participants} is a single-
 * element list (the one linked character/role that earned this time) — kept as a list, not a bare
 * {@link ParticipantSummary}, so the frontend's "Users" column can render it the same way as
 * {@link SectionEntryResponse}'s. {@code runId} lets the frontend link this row to its run detail
 * page, same as {@link SectionEntryResponse}'s.
 */
public record PersonalSectionBestResponse(Long runId, Long durationMs, Long startMs, Long doneMs, List<ParticipantSummary> participants) {
}
