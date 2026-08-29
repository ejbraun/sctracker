package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.Run;

import java.time.Instant;
import java.util.List;

public record RunDetailResponse(Long runId, Integer mapId, String mapName, Instant utcStart, Long instanceStartMs,
                                 String endReason, boolean completed, Long durationMs, Integer partySize,
                                 List<ObjectiveEntry> objectives, List<ParticipantEntry> participants,
                                 List<RunFailureReasonEntry> failureReasons, RunMvpAwardEntry mvpAward) {

    public static RunDetailResponse from(Run run, List<ObjectiveEntry> objectives, List<ParticipantEntry> participants,
                                          List<RunFailureReasonEntry> failureReasons, RunMvpAwardEntry mvpAward) {
        return new RunDetailResponse(run.getId(), run.getMap().getId(), run.getMap().getName(), run.getUtcStart(),
                run.getInstanceStartMs(), run.getEndReason(), run.isCompleted(), run.getDurationMs(), run.getPartySize(),
                objectives, participants, failureReasons, mvpAward);
    }
}
