package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RunFailureReasonId implements Serializable {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    protected RunFailureReasonId() {
    }

    public RunFailureReasonId(Long runId, String role) {
        this.runId = runId;
        this.role = role;
    }

    public Long getRunId() {
        return runId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RunFailureReasonId that)) return false;
        return Objects.equals(runId, that.runId) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, role);
    }
}
