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
@Table(name = "run_participants")
public class RunParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private PlayerCharacter character;

    @Column(name = "raw_name", nullable = false, length = 64)
    private String rawName;

    // TINYINT UNSIGNED, matching professions.id — see Profession.id's comment. Join column type
    // isn't inherited from the referenced entity's @Column, so it needs its own columnDefinition.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_profession_id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Profession primaryProfession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_profession_id", columnDefinition = "TINYINT UNSIGNED")
    private Profession secondaryProfession;

    @Column(length = 16)
    private String role;

    // TINYINT UNSIGNED — see Profession.id's comment.
    @Column(name = "party_index", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer partyIndex;

    /**
     * Found in a real payload sample, not in the original spec draft: party slots can be
     * AI-controlled heroes/henchmen, not just human players. Stored for fidelity now; whether
     * these should affect role derivation, party-size validation, or personal-best eligibility is
     * an open question — see IMPLEMENTATION_PROGRESS.md.
     */
    @Column(name = "is_player", nullable = false)
    private boolean isPlayer;

    @Column(name = "is_hero", nullable = false)
    private boolean isHero;

    @Column(name = "is_henchman", nullable = false)
    private boolean isHenchman;

    // SMALLINT UNSIGNED — see Profession.id's comment. Found in a real payload sample, not in the
    // original spec draft, same situation as is_player/is_hero/is_henchman above.
    @Column(nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer deaths;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected RunParticipant() {
    }

    public RunParticipant(Run run, PlayerCharacter character, String rawName,
                           Profession primaryProfession, Profession secondaryProfession,
                           String role, Integer partyIndex, boolean isPlayer, boolean isHero, boolean isHenchman,
                           Integer deaths) {
        this.run = run;
        this.character = character;
        this.rawName = rawName;
        this.primaryProfession = primaryProfession;
        this.secondaryProfession = secondaryProfession;
        this.role = role;
        this.partyIndex = partyIndex;
        this.isPlayer = isPlayer;
        this.isHero = isHero;
        this.isHenchman = isHenchman;
        this.deaths = deaths;
    }

    public Long getId() {
        return id;
    }

    public Run getRun() {
        return run;
    }

    public PlayerCharacter getCharacter() {
        return character;
    }

    public void setCharacter(PlayerCharacter character) {
        this.character = character;
    }

    public String getRawName() {
        return rawName;
    }

    public Profession getPrimaryProfession() {
        return primaryProfession;
    }

    public void setPrimaryProfession(Profession primaryProfession) {
        this.primaryProfession = primaryProfession;
    }

    public Profession getSecondaryProfession() {
        return secondaryProfession;
    }

    public void setSecondaryProfession(Profession secondaryProfession) {
        this.secondaryProfession = secondaryProfession;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getPartyIndex() {
        return partyIndex;
    }

    public boolean isPlayer() {
        return isPlayer;
    }

    public void setPlayer(boolean player) {
        isPlayer = player;
    }

    public boolean isHero() {
        return isHero;
    }

    public void setHero(boolean hero) {
        isHero = hero;
    }

    public boolean isHenchman() {
        return isHenchman;
    }

    public void setHenchman(boolean henchman) {
        isHenchman = henchman;
    }

    public Integer getDeaths() {
        return deaths;
    }

    public void setDeaths(Integer deaths) {
        this.deaths = deaths;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
