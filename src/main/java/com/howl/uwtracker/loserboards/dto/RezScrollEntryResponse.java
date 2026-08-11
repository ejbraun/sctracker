package com.howl.uwtracker.loserboards.dto;

import java.time.Instant;

/**
 * One participant's rez_scroll_uses in a single run — "Most res scroll uses in a run." Per-player,
 * not summed across the party: each row is one (run, user) performance, worst (highest) first.
 */
public record RezScrollEntryResponse(Long runId, Instant utcStart, String user, String role, Integer rezScrollUses) {
}
