package com.howl.uwtracker.auth.dto;

import com.howl.uwtracker.domain.Person;

public record PersonResponse(Long id, String username, String alias) {

    public static PersonResponse from(Person person) {
        return new PersonResponse(person.getId(), person.getUsername(), person.getAlias());
    }
}
