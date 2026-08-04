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
@Table(name = "signup_keys")
public class SignupKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CHAR(64), not VARCHAR — a SHA-256 hex digest is always exactly 64 characters. Same
    // columnDefinition caveat as MachineKey.keyHash: Hibernate's schema validator assumes
    // VARCHAR(64) for a bare String field and fails to boot against a real MySQL otherwise.
    @Column(name = "key_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String keyHash;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    // Null until used — a signup key isn't tied to a person until someone actually redeems it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_person_id")
    private Person usedByPerson;

    protected SignupKey() {
    }

    public SignupKey(String keyHash) {
        this.keyHash = keyHash;
    }

    public Long getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Person getUsedByPerson() {
        return usedByPerson;
    }

    public void markUsed(Person person) {
        this.usedAt = Instant.now();
        this.usedByPerson = person;
    }
}
