package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "professions")
public class Profession {

    // TINYINT UNSIGNED (0-255 is plenty for a profession count) — needs an explicit columnDefinition;
    // Hibernate's schema validator maps a bare Integer field to INTEGER (4-byte) by default, which
    // is a different JDBC type category than TINYINT (1-byte) and fails validation against a real
    // MySQL otherwise (see MachineKey.keyHash's comment for the same class of issue).
    @Id
    @Column(columnDefinition = "TINYINT UNSIGNED")
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    protected Profession() {
    }

    public Profession(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
