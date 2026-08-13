package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One (run, role) pair a permitted reporter flagged as at fault for that run's failure, via
 * POST /report-run-failure. A resubmission wholesale-replaces a run's rows (see
 * FailureReportService), so this is always the latest report for that run, not an append-only log.
 */
@Entity
@Table(name = "run_failure_reasons")
public class RunFailureReason {

    @EmbeddedId
    private RunFailureReasonId id;

    @Column(name = "reported_by_person_id")
    private Long reportedByPersonId;

    @Column(name = "reported_at", nullable = false, updatable = false, insertable = false)
    private Instant reportedAt;

    protected RunFailureReason() {
    }

    public RunFailureReason(RunFailureReasonId id, Long reportedByPersonId) {
        this.id = id;
        this.reportedByPersonId = reportedByPersonId;
    }

    public RunFailureReasonId getId() {
        return id;
    }

    public Long getReportedByPersonId() {
        return reportedByPersonId;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }
}
