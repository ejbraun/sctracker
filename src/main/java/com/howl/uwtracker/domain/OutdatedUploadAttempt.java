package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One /upload-run attempt rejected with 426 Upgrade Required (see
 * MachineKeyAuthenticationService#authenticateForUpload) — backs the "most outdated-plugin upload
 * attempts by user" loserboard. A raw {@code personId} column, not a {@code @ManyToOne}, same
 * reasoning as {@link RunFailureReason#getReportedByPersonId()}: this row only ever needs the id
 * for attribution/aggregation, never the full Person.
 */
@Entity
@Table(name = "outdated_upload_attempts")
public class OutdatedUploadAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "attempted_plugin_version")
    private Integer attemptedPluginVersion;

    @Column(name = "attempted_at", nullable = false, updatable = false, insertable = false)
    private Instant attemptedAt;

    protected OutdatedUploadAttempt() {
    }

    public OutdatedUploadAttempt(Long personId, Integer attemptedPluginVersion) {
        this.personId = personId;
        this.attemptedPluginVersion = attemptedPluginVersion;
    }

    public Long getId() {
        return id;
    }

    public Long getPersonId() {
        return personId;
    }

    public Integer getAttemptedPluginVersion() {
        return attemptedPluginVersion;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
