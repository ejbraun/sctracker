package com.howl.uwtracker.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves a controller method parameter to the logged-in person's id from the session — see
 * specs/backend/03-auth.md. Safe to assume present on any /api/** endpoint other than
 * signup/login, since {@link SessionAuthInterceptor} already rejects unauthenticated requests
 * with 401 before the controller method runs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentPersonId {
}
