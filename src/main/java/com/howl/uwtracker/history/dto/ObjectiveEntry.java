package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.RunObjective;

public record ObjectiveEntry(Integer sequence, String name, Integer status, Long startMs, Long doneMs,
                              Long durationMs, Integer indent) {

    public static ObjectiveEntry from(RunObjective ro) {
        return new ObjectiveEntry(ro.getSequence(), ro.getName(), ro.getStatus(), ro.getStartMs(), ro.getDoneMs(),
                ro.getDurationMs(), ro.getIndent());
    }
}
