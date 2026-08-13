package com.howl.uwtracker.auth;

import com.howl.uwtracker.repository.AdminRepository;
import com.howl.uwtracker.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/admin/** in addition to {@link SessionAuthInterceptor} (which already guards
 * /api/**, and runs first per WebMvcConfig's registration order, so an unauthenticated request
 * never reaches this check at all).
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminRepository adminRepository;

    public AdminAuthInterceptor(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Long personId = session == null ? null : (Long) session.getAttribute(SessionKeys.PERSON_ID);
        if (personId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }
        if (!adminRepository.existsById(personId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin only");
        }
        return true;
    }
}
