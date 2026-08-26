package com.howl.uwtracker.mvpreports.dto;

import java.util.List;

/**
 * {@code roles} is single-select on this endpoint — 0 or 1 entries, never more (enforced in
 * MvpReportService, not trusted from the client) — unlike failurereports' identically-shaped
 * request, which is still multi-select. Same wire shape as that request by coincidence, not by a
 * rule the two need to stay in sync on; kept as its own type rather than reused for that reason (see
 * MvpBallot's doc).
 */
public record ReportRunMvpRequest(Long runId, List<String> roles) {
}
