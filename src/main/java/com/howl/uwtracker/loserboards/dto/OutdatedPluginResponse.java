package com.howl.uwtracker.loserboards.dto;

import java.time.Instant;

/**
 * One row of the "Players On An Outdated Plugin" Loserboards widget: a user whose plugin last
 * authenticated (any machine-key call — most reliably the once-per-load GET /can-report-run-failure)
 * on a version below the current minimum, within the selected time window. {@code pluginVersion} is
 * null for a client too old to send the {@code X-Plugin-Version} header. Not map-scoped — the
 * version check happens before any request body is read.
 */
public record OutdatedPluginResponse(String user, Integer pluginVersion, Instant lastSeen) {
}
