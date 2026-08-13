package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A row's mere existence for a person_id makes them an admin — membership, not a boolean flag.
 * Nothing in the app writes this table; admins are added by hand (direct INSERT). Gates
 * /api/admin/** via AdminAuthInterceptor.
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

    public Long getPersonId() {
        return personId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
