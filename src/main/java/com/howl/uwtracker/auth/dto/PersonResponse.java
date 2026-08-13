package com.howl.uwtracker.auth.dto;

import com.howl.uwtracker.domain.Person;

public record PersonResponse(Long id, String username, String alias, boolean newPluginVersionAvailable, boolean isAdmin) {

    public static PersonResponse from(Person person, boolean newPluginVersionAvailable, boolean isAdmin) {
        return new PersonResponse(person.getId(), person.getUsername(), person.getAlias(), newPluginVersionAvailable, isAdmin);
    }
}
