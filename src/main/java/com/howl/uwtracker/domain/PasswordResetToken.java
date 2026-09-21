package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An admin-minted, single-use, expiring link that lets one user set a new password. Claimed by
 * {@code PasswordResetTokenRepository.tryClaim} (an atomic conditional UPDATE), never a setter
 * here. Only {@code SHA-256(token)} hex is stored.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    // CHAR(64), not VARCHAR — see SignupLink.tokenHash.
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_by_person_id")
    private Long createdByPersonId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(Long personId, String tokenHash, Instant expiresAt, Long createdByPersonId) {
        this.personId = personId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdByPersonId = createdByPersonId;
    }

    public Long getId() {
        return id;
    }

    public Long getPersonId() {
        return personId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
