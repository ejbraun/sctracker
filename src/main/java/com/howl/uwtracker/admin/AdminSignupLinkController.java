package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.CreateSignupLinkRequest;
import com.howl.uwtracker.admin.dto.GeneratedSignupLinkResponse;
import com.howl.uwtracker.admin.dto.SignupLinkResponse;
import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.signuplinks.SignupLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * specs/backend/03-auth.md — admin-managed multi-use signup links. Gated centrally by
 * {@code AdminAuthInterceptor} on {@code /api/admin/**} (401 no session, 403 not an admin), so no
 * per-method auth here.
 */
@RestController
@RequestMapping("/api/admin/signup-links")
public class AdminSignupLinkController {

    private final SignupLinkService signupLinkService;

    public AdminSignupLinkController(SignupLinkService signupLinkService) {
        this.signupLinkService = signupLinkService;
    }

    @PostMapping
    public ResponseEntity<GeneratedSignupLinkResponse> create(@CurrentPersonId Long adminPersonId,
                                                              @RequestBody CreateSignupLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signupLinkService.create(adminPersonId, request.label(), request.maxUses()));
    }

    @GetMapping
    public ResponseEntity<List<SignupLinkResponse>> list() {
        return ResponseEntity.ok(signupLinkService.list());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        signupLinkService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
