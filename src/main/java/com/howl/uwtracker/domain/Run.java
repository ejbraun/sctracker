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

@Entity
@Table(name = "runs")
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private GameMap map;

    @Column(name = "utc_start", nullable = false)
    private Instant utcStart;

    /**
     * NOT a timestamp — confirmed against a real GWToolboxdll payload sample: this is a
     * std::chrono::steady_clock-based (or TimerWidget load-screen) millisecond counter, zeroed at
     * an arbitrary point tied to system boot. It has no absolute meaning and isn't comparable
     * across runs/machines; it only exists as the zero-point each objective's start/done/duration
     * is measured relative to. Storing it as a raw offset, not attempting a DATETIME conversion —
     * an earlier draft of this column was typed DATETIME(6), which would have silently produced a
     * syntactically valid but meaningless date.
     */
    @Column(name = "instance_start_ms")
    private Long instanceStartMs;

    @Column(name = "objective_start")
    private Instant objectiveStart;

    @Column(name = "end_reason", nullable = false, length = 16)
    private String endReason;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Run() {
    }

    public Run(GameMap map, Instant utcStart, Long instanceStartMs, Instant objectiveStart,
               String endReason, boolean completed, Long durationMs) {
        this.map = map;
        this.utcStart = utcStart;
        this.instanceStartMs = instanceStartMs;
        this.objectiveStart = objectiveStart;
        this.endReason = endReason;
        this.completed = completed;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public GameMap getMap() {
        return map;
    }

    public Instant getUtcStart() {
        return utcStart;
    }

    public Long getInstanceStartMs() {
        return instanceStartMs;
    }

    public Instant getObjectiveStart() {
        return objectiveStart;
    }

    public String getEndReason() {
        return endReason;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
