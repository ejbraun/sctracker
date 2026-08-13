package com.howl.uwtracker.leaderboards.dto;

/**
 * One (item, user) row for the "Luckiest Players" panel — total reserved drops of that tracked
 * item, summed across every run this user participated in on the map, plus their average per run
 * (total divided by every run they've participated in on this map, not just runs the item dropped
 * in) — luckiest by that average first.
 */
public record ItemDropLeaderResponse(Integer itemId, String itemName, String user, Long totalCount, Double avgPerRun) {
}
