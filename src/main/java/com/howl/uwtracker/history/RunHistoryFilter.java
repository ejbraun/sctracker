package com.howl.uwtracker.history;

import java.time.Instant;

public record RunHistoryFilter(Long personId, Long characterId, String role, Integer mapId, Integer partySize,
                                Instant from, Instant to, Boolean completed, String endReason) {
}
