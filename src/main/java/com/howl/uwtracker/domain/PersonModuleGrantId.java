package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PersonModuleGrantId implements Serializable {

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    protected PersonModuleGrantId() {
    }

    public PersonModuleGrantId(Long personId, Long moduleId) {
        this.personId = personId;
        this.moduleId = moduleId;
    }

    public Long getPersonId() {
        return personId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonModuleGrantId that)) return false;
        return Objects.equals(personId, that.personId) && Objects.equals(moduleId, that.moduleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personId, moduleId);
    }
}
