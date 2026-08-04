package com.howl.uwtracker.leaderboards;

/** Raw row from {@link LeaderboardQueryRepository#findPersonalSectionBestRun} — participants (the full gated party of that run, not just the person's own character) are assembled separately in {@link LeaderboardService}. */
record PersonalSectionBestRunRef(Long runId, Long durationMs, Long startMs, Long doneMs) {
}
