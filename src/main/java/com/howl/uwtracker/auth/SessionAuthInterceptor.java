package com.howl.uwtracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Guards /api/** (other than signup/login/password-reset, excluded via path patterns in WebMvcConfig) — specs/backend/03-auth.md.
 * /upload-run is authenticated separately via machine key and isn't under /api/** at all, so this
 * interceptor never runs on it.
 *
 * <p>Also enforces {@code people.sessions_valid_after}: a session that logged in before the
 * person's last password reset is rejected (sessions predating the attribute count as old).
 */
public class SessionAuthInterceptor implements HandlerInterceptor {

    private final PersonRepository personRepository;

    public SessionAuthInterceptor(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object personId = session == null ? null : session.getAttribute(SessionKeys.PERSON_ID);
        if (personId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }
        Instant validAfter = personRepository.findSessionsValidAfterById((Long) personId).orElse(null);
        if (validAfter != null) {
            Object authenticatedAt = session.getAttribute(SessionKeys.AUTHENTICATED_AT);
            if (!(authenticatedAt instanceof Long millis) || Instant.ofEpochMilli(millis).isBefore(validAfter)) {
                session.invalidate();
                throw new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated");
            }
        }
        return true;
    }
}
