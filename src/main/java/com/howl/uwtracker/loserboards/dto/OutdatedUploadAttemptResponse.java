package com.howl.uwtracker.loserboards.dto;

/** Per-user count of /upload-run attempts rejected with 426 Upgrade Required. Not map-scoped. */
public record OutdatedUploadAttemptResponse(String user, Long attempts) {
}
