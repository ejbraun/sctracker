package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An admin-minted, multi-use invite for {@code POST /api/signup} — the alternative to a single-use
 * {@link SignupKey}. One link absorbs up to {@link #maxUses} signups; {@link #useCount} is bumped
 * by {@code SignupLinkRepository.tryClaim} (an atomic conditional UPDATE), never a setter here.
 * Soft-revocable via {@link #revokedAt}, like {@link MachineKey}. Only {@code SHA-256(token)} hex
 * is stored. See specs/backend/03-auth.md.
 */
@Entity
@Table(name = "signup_links")
public class SignupLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CHAR(64), not VARCHAR — same columnDefinition caveat as SignupKey.keyHash / MachineKey.keyHash:
    // Hibernate's schema validator assumes VARCHAR(64) for a bare String field and fails to boot
    // against a real MySQL otherwise.
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(length = 64)
    private String label;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    // Null once the admin who minted it is deleted (FK ON DELETE SET NULL) — the link keeps working.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_person_id")
    private Person createdByPerson;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SignupLink() {
    }

    public SignupLink(String tokenHash, String label, int maxUses, Person createdByPerson) {
        this.tokenHash = tokenHash;
        this.label = label;
        this.maxUses = maxUses;
        this.createdByPerson = createdByPerson;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getLabel() {
        return label;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getUseCount() {
        return useCount;
    }

    public Person getCreatedByPerson() {
        return createdByPerson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
