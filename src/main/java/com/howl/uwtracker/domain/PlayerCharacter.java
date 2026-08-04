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
@Table(name = "characters")
public class PlayerCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "character_name", nullable = false, unique = true, length = 64)
    private String characterName;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected PlayerCharacter() {
    }

    public PlayerCharacter(Person person, String characterName) {
        this.person = person;
        this.characterName = characterName;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public String getCharacterName() {
        return characterName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
