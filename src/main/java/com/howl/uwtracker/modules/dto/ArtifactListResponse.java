package com.howl.uwtracker.modules.dto;

import java.util.List;

/** Body of {@code GET /artifacts} — every enabled artifact (launcher + plugins), public or not. */
public record ArtifactListResponse(List<ArtifactSummaryResponse> artifacts) {
}
