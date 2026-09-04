package com.howl.uwtracker.mvpreports.dto;

import java.util.List;

/**
 * {@code roles} is single-select on this endpoint — 0 or 1 entries, never more (enforced in
 * MvpReportService, not trusted from the client) — unlike failurereports' identically-shaped
 * request, which is still multi-select. Same wire shape as that request by coincidence, not by a
 * rule the two need to stay in sync on; kept as its own type rather than reused for that reason (see
 * MvpBallot's doc).
 *
 * <p>Despite the field name, {@code roles} holds a role name only for a run whose {@code (map,
 * party_size)} config has a role model; for a role-less config it holds a party member's character
 * {@code raw_name} instead (see specs/features/fow-and-party-size.md §9.6). The wire field wasn't
 * renamed to avoid a JSON-shape change on top of the semantic one — {@code MvpPersister} is what
 * resolves which interpretation applies, per run.
 */
public record ReportRunMvpRequest(Long runId, List<String> roles) {
}
