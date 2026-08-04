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

@Entity
@Table(name = "run_objectives")
public class RunObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    // SMALLINT UNSIGNED — see Profession.id's comment: a bare Integer field needs an explicit
    // columnDefinition to match a narrower-than-INTEGER MySQL column under schema validation.
    @Column(nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer sequence;

    @Column(nullable = false, length = 128)
    private String name;

    // TINYINT UNSIGNED — see Profession.id's comment.
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer status;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "done_ms")
    private Long doneMs;

    @Column(name = "duration_ms")
    private Long durationMs;

    /** Nesting depth from the payload — found in a real sample, always 0 so far; stored for fidelity, not yet used. */
    // TINYINT UNSIGNED — see Profession.id's comment.
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer indent;

    protected RunObjective() {
    }

    public RunObjective(Run run, Integer sequence, String name, Integer status,
                         Long startMs, Long doneMs, Long durationMs, Integer indent) {
        this.run = run;
        this.sequence = sequence;
        this.name = name;
        this.status = status;
        this.startMs = startMs;
        this.doneMs = doneMs;
        this.durationMs = durationMs;
        this.indent = indent;
    }

    public Long getId() {
        return id;
    }

    public Run getRun() {
        return run;
    }

    public Integer getSequence() {
        return sequence;
    }

    public String getName() {
        return name;
    }

    public Integer getStatus() {
        return status;
    }

    public Long getStartMs() {
        return startMs;
    }

    public Long getDoneMs() {
        return doneMs;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getIndent() {
        return indent;
    }
}
