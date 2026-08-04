package com.howl.uwtracker.ingestion.dto;

/**
 * Top-level POST /upload-run payload — see specs/backend/02-ingestion-upload-run.md.
 * Wire format is snake_case (spring.jackson.property-naming-strategy=SNAKE_CASE).
 */
public record UploadRunRequest(PartyDto party, ObjectiveSectionDto objective) {
}
