package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One run_participant a permitted reporter flagged as at fault for a run's failure, via
 * POST /report-run-failure. runParticipant is null to record "Nobody was at fault" instead of a
 * specific participant. A resubmission wholesale-replaces a run's rows (see FailureReportService),
 * so this is always the latest report for that run, not an append-only log.
 */
@Entity
@Table(name = "run_failure_reasons")
public class RunFailureReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_participant_id")
    private RunParticipant runParticipant;

    @Column(name = "reported_by_person_id")
    private Long reportedByPersonId;

    @Column(name = "reported_at", nullable = false, updatable = false, insertable = false)
    private Instant reportedAt;

    protected RunFailureReason() {
    }

    public RunFailureReason(Run run, RunParticipant runParticipant, Long reportedByPersonId) {
        this.run = run;
        this.runParticipant = runParticipant;
        this.reportedByPersonId = reportedByPersonId;
    }

    public Long getId() {
        return id;
    }

    public Run getRun() {
        return run;
    }

    public RunParticipant getRunParticipant() {
        return runParticipant;
    }

    public Long getReportedByPersonId() {
        return reportedByPersonId;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }
}
