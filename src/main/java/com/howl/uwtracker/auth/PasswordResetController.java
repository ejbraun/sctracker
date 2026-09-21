package com.howl.uwtracker.auth;

import com.howl.uwtracker.auth.dto.ResetPasswordRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (no session) — excluded from {@link SessionAuthInterceptor} in WebMvcConfig. */
@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping
    public ResponseEntity<Void> reset(@RequestBody ResetPasswordRequest request) {
        passwordResetService.redeem(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }
}
