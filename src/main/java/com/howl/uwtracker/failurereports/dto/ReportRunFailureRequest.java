package com.howl.uwtracker.failurereports.dto;

import java.util.List;

/**
 * Despite the field name, {@code roles} holds role names only for a run whose {@code (map,
 * party_size)} config has a role model; for a role-less config it holds party members' character
 * {@code raw_name}s instead (see specs/features/fow-and-party-size.md §9.6). The wire field wasn't
 * renamed to avoid a JSON-shape change on top of the semantic one — {@code FailureReportPersister}
 * is what resolves which interpretation applies, per run.
 */
public record ReportRunFailureRequest(Long runId, List<String> roles) {
}
