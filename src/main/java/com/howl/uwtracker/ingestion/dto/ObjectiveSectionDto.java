package com.howl.uwtracker.ingestion.dto;

import java.util.List;

public record ObjectiveSectionDto(
        String name,
        Long instanceStart,
        Long utcStart,
        List<ObjectiveDto> objectives,
        Long duration
) {
}
