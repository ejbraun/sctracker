package com.howl.uwtracker.auth;

public final class SessionKeys {

    public static final String PERSON_ID = "personId";

    // Epoch millis of the login that created this session — compared against
    // people.sessions_valid_after by SessionAuthInterceptor.
    public static final String AUTHENTICATED_AT = "authenticatedAt";

    private SessionKeys() {
    }
}
