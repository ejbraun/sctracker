package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A row's mere existence for a person_id makes them an admin — membership, not a boolean flag.
 * Gates /api/admin/** via AdminAuthInterceptor. Written by an existing admin via
 * {@code PATCH /api/admin/users/{personId}/admin} (AdminUserController); the bootstrap first admin
 * is still a hand INSERT.
 */
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "granted_at", nullable = false, updatable = false, insertable = false)
    private Instant grantedAt;

    protected Admin() {
    }

    public Admin(Long personId) {
        this.personId = personId;
    }

    public Long getPersonId() {
        return personId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
