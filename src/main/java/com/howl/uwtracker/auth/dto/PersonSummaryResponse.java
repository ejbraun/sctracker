package com.howl.uwtracker.auth.dto;

import com.howl.uwtracker.domain.Person;

/**
 * Minimal, alias-only view of another person — backs the Run History "person" filter dropdown.
 * Deliberately doesn't include {@code username} (private, login-only) alongside {@code id} (an
 * internal identifier the frontend uses to build the filter query, never shown to the user).
 */
public record PersonSummaryResponse(Long id, String alias) {

    public static PersonSummaryResponse from(Person person) {
        return new PersonSummaryResponse(person.getId(), person.getAlias());
    }
}
