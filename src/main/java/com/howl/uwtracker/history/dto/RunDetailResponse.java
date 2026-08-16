package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.Run;

import java.time.Instant;
import java.util.List;

public record RunDetailResponse(Long runId, Integer mapId, String mapName, Instant utcStart, Long instanceStartMs,
                                 String endReason, boolean completed, Long durationMs,
                                 List<ObjectiveEntry> objectives, List<ParticipantEntry> participants,
                                 List<RunFailureReasonEntry> failureReasons) {

    public static RunDetailResponse from(Run run, List<ObjectiveEntry> objectives, List<ParticipantEntry> participants,
                                          List<RunFailureReasonEntry> failureReasons) {
        return new RunDetailResponse(run.getId(), run.getMap().getId(), run.getMap().getName(), run.getUtcStart(),
                run.getInstanceStartMs(), run.getEndReason(), run.isCompleted(), run.getDurationMs(), objectives,
                participants, failureReasons);
    }
}
