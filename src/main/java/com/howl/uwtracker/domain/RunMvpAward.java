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
 * The run_participant a permitted reporter credited as the standout on a successful run, via
 * POST /report-run-mvp. runParticipant is null to record "Nobody" was singled out instead of a
 * specific participant. A resubmission wholesale-replaces a run's row (see MvpReportPersister), so
 * this is always the latest majority outcome for that run, not an append-only log. Unlike
 * {@link RunFailureReason}, at most one row ever exists per run — see the changelog for why.
 */
@Entity
@Table(name = "run_mvp_awards")
public class RunMvpAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_participant_id")
    private RunParticipant runParticipant;

    @Column(name = "awarded_by_person_id")
    private Long awardedByPersonId;

    @Column(name = "awarded_at", nullable = false, updatable = false, insertable = false)
    private Instant awardedAt;

    protected RunMvpAward() {
    }

    public RunMvpAward(Run run, RunParticipant runParticipant, Long awardedByPersonId) {
        this.run = run;
        this.runParticipant = runParticipant;
        this.awardedByPersonId = awardedByPersonId;
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

    public Long getAwardedByPersonId() {
        return awardedByPersonId;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }
}
