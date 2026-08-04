package com.howl.uwtracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/** (other than signup/login, excluded via path patterns in WebMvcConfig) — specs/backend/03-auth.md.
 * /upload-run is authenticated separately via machine key and isn't under /api/** at all, so this
 * interceptor never runs on it.
 */
public class SessionAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKeys.PERSON_ID) == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }
        return true;
    }
}
