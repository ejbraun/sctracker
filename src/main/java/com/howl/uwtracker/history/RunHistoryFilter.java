package com.howl.uwtracker.history;

import java.time.Instant;

public record RunHistoryFilter(Long personId, Long characterId, String role, Integer mapId,
                                Instant from, Instant to, Boolean completed, String endReason) {
}
