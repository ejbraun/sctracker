package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MapConfigId implements Serializable {

    @Column(name = "map_id", nullable = false)
    private Integer mapId;

    // columnDefinition matches the migration's TINYINT UNSIGNED — without it Hibernate's schema
    // validation expects a plain INTEGER column and fails at boot (same trick as Run.partySize /
    // RunParticipant.partyIndex).
    @Column(name = "party_size", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer partySize;

    protected MapConfigId() {
    }

    public MapConfigId(Integer mapId, Integer partySize) {
        this.mapId = mapId;
        this.partySize = partySize;
    }

    public Integer getMapId() {
        return mapId;
    }

    public Integer getPartySize() {
        return partySize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapConfigId that)) return false;
        return Objects.equals(mapId, that.mapId) && Objects.equals(partySize, that.partySize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapId, partySize);
    }
}
