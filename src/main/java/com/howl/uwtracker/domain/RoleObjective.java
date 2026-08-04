package com.howl.uwtracker.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_objectives")
public class RoleObjective {

    @EmbeddedId
    private RoleObjectiveId id;

    protected RoleObjective() {
    }

    public RoleObjective(RoleObjectiveId id) {
        this.id = id;
    }

    public RoleObjectiveId getId() {
        return id;
    }
}
