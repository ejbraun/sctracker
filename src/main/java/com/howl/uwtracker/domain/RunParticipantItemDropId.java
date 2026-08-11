package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RunParticipantItemDropId implements Serializable {

    @Column(name = "run_participant_id", nullable = false)
    private Long runParticipantId;

    @Column(name = "item_id", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer itemId;

    protected RunParticipantItemDropId() {
    }

    public RunParticipantItemDropId(Long runParticipantId, Integer itemId) {
        this.runParticipantId = runParticipantId;
        this.itemId = itemId;
    }

    public Long getRunParticipantId() {
        return runParticipantId;
    }

    public Integer getItemId() {
        return itemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RunParticipantItemDropId that)) return false;
        return Objects.equals(runParticipantId, that.runParticipantId) && Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runParticipantId, itemId);
    }
}
