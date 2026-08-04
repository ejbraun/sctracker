package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.Run;

import java.time.Instant;

public record RunSummaryResponse(Long runId, Integer mapId, String mapName, Instant utcStart, String endReason,
                                  boolean completed, Long durationMs, int participantCount) {

    public static RunSummaryResponse from(Run run, int participantCount) {
        return new RunSummaryResponse(run.getId(), run.getMap().getId(), run.getMap().getName(), run.getUtcStart(),
                run.getEndReason(), run.isCompleted(), run.getDurationMs(), participantCount);
    }
}
