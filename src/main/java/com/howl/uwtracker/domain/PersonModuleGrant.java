package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One person's entitlement to one gated {@link Module}. Existence of the row == access; there is no
 * "revoked" state — a revoke deletes it. Granted/revoked by an admin via
 * {@code /api/admin/users/{personId}/modules/{moduleKey}} (AdminUserController). Public modules need
 * no row here. See specs/backend/08-module-entitlements.md.
 */
@Entity
@Table(name = "person_module_grants")
public class PersonModuleGrant {

    @EmbeddedId
    private PersonModuleGrantId id;

    @Column(name = "granted_at", nullable = false, updatable = false, insertable = false)
    private Instant grantedAt;

    /** people.id of the admin who granted this; {@code null} once that account is gone (FK ON DELETE SET NULL). */
    @Column(name = "granted_by")
    private Long grantedBy;

    protected PersonModuleGrant() {
    }

    public PersonModuleGrant(PersonModuleGrantId id, Long grantedBy) {
        this.id = id;
        this.grantedBy = grantedBy;
    }

    public PersonModuleGrantId getId() {
        return id;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Long getGrantedBy() {
        return grantedBy;
    }
}
