package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RoleObjectiveId implements Serializable {

    @Column(name = "map_id", nullable = false)
    private Integer mapId;

    @Column(name = "objective_name", nullable = false, length = 128)
    private String objectiveName;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    protected RoleObjectiveId() {
    }

    public RoleObjectiveId(Integer mapId, String objectiveName, String role) {
        this.mapId = mapId;
        this.objectiveName = objectiveName;
        this.role = role;
    }

    public Integer getMapId() {
        return mapId;
    }

    public String getObjectiveName() {
        return objectiveName;
    }

    public String getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleObjectiveId that)) return false;
        return Objects.equals(mapId, that.mapId)
                && Objects.equals(objectiveName, that.objectiveName)
                && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapId, objectiveName, role);
    }
}
