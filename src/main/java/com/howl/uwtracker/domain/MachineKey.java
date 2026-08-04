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

@Entity
@Table(name = "machine_keys")
public class MachineKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    // CHAR(64), not VARCHAR — a SHA-256 hex digest is always exactly 64 characters (see the
    // matching changeset, 004-create-machine-keys.xml). Needs an explicit columnDefinition: without
    // it Hibernate's schema validator assumes VARCHAR(64) for any String field and fails to boot
    // against a real MySQL with "wrong column type... found [char], but expecting [varchar(64)]" —
    // this discrepancy was invisible to mvn compile/test and only surfaced once a real DB (via
    // Testcontainers) actually validated the schema.
    @Column(name = "key_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String keyHash;

    @Column(length = 64)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MachineKey() {
    }

    public MachineKey(Person person, String keyHash, String label) {
        this.person = person;
        this.keyHash = keyHash;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getLabel() {
        return label;
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
