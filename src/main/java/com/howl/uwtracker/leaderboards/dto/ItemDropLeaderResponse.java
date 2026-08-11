package com.howl.uwtracker.leaderboards.dto;

/**
 * One (item, user) row for the "Luckiest Players" panel — total reserved drops of that tracked
 * item, summed across every run this user participated in on the map, luckiest (highest count) first.
 */
public record ItemDropLeaderResponse(Integer itemId, String itemName, String user, Long totalCount) {
}
