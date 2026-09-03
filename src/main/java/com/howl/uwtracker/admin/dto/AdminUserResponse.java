package com.howl.uwtracker.admin.dto;

import com.howl.uwtracker.domain.Person;

/** Row shape for the admin "User Management" table. {@code isAdmin} is toggled via {@code PATCH .../{id}/admin}. */
public record AdminUserResponse(Long id, String username, String alias, boolean canReportFailures, boolean isAdmin) {

    public static AdminUserResponse from(Person person, boolean isAdmin) {
        return new AdminUserResponse(person.getId(), person.getUsername(), person.getAlias(),
                person.isCanReportFailures(), isAdmin);
    }
}
