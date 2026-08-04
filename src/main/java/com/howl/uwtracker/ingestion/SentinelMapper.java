package com.howl.uwtracker.ingestion;

/**
 * 4294967295 (2^32 - 1, uint32 max) in any numeric field of the /upload-run payload means
 * "not reached" and must be mapped to null before persisting — specs/backend/00-overview.md.
 */
public final class SentinelMapper {

    public static final long SENTINEL = 4294967295L;

    private SentinelMapper() {
    }

    public static Long map(Long value) {
        return (value == null || value == SENTINEL) ? null : value;
    }
}
