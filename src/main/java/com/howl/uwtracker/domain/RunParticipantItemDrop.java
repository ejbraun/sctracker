package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One (participant, tracked item) pair — how many times that item was reserved for this member
 * during the run (GAME_SMSG_ITEM_UPDATE_OWNER on the plugin side; a strong proxy for "who got the
 * item," not confirmed pickup — see the item_drops payload summary). {@code count} is stored as
 * {@code drop_count}: COUNT is a reserved word in MySQL.
 */
@Entity
@Table(name = "run_participant_item_drops")
public class RunParticipantItemDrop {

    @EmbeddedId
    private RunParticipantItemDropId id;

    @Column(name = "drop_count", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer count;

    protected RunParticipantItemDrop() {
    }

    public RunParticipantItemDrop(RunParticipantItemDropId id, Integer count) {
        this.id = id;
        this.count = count;
    }

    public RunParticipantItemDropId getId() {
        return id;
    }

    public Integer getCount() {
        return count;
    }
}
