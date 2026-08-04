package com.howl.uwtracker.leaderboards;

import java.time.Instant;

/** Raw row from {@link LeaderboardQueryRepository#findPersonalOverallTop} — participants are assembled separately in {@link LeaderboardService}. */
record PersonalBestRunRef(Long runId, Long durationMs, Instant utcStart) {
}
