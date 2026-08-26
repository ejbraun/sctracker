package com.howl.uwtracker.leaderboards.dto;

/**
 * "Gamblers Anonymous" — per-user net Ghastly Summoning Stones won (positive) or lost (negative)
 * gambling with other party members at the end of a successful run, summed across every completed
 * run on the map where the user actually gambled (a null {@code gambling_stone_net} on a
 * participant row means they didn't gamble that run, not that they broke even — see
 * {@code RunParticipant.gamblingStoneNet}). {@code runsGambled} counts only those actually-gambled
 * runs, not every completed run the user played in. Ranked biggest net winner first.
 */
public record GamblingStoneLeaderResponse(String user, Long runsGambled, Long netStones) {
}
