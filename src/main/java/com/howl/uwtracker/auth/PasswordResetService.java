package com.howl.uwtracker.auth;

import com.howl.uwtracker.auth.dto.GeneratedPasswordResetResponse;
import com.howl.uwtracker.domain.PasswordResetToken;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.repository.PasswordResetTokenRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Admin-issued password reset. An admin mints a single-use, 24h link for a specific user; whoever
 * holds it can set that user's password. There is no email on {@code people}, so the admin hands
 * the link over out of band (same as signup links). Tokens are hashed like machine keys
 * ({@link MachineKeyHasher}).
 */
@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(PersonRepository personRepository, PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Supersedes any outstanding link for the person. The raw token is returned once, never stored. */
    @Transactional
    public GeneratedPasswordResetResponse create(Long personId, Long adminPersonId) {
        personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        Instant now = Instant.now();
        tokenRepository.invalidateOutstanding(personId, now);

        String token = MachineKeyHasher.generateRawKey();
        Instant expiresAt = now.plus(TOKEN_TTL);
        tokenRepository.save(new PasswordResetToken(personId, MachineKeyHasher.hash(token), expiresAt, adminPersonId));
        return new GeneratedPasswordResetResponse(token, expiresAt);
    }

    /**
     * Sets the new password and revokes the person's existing sessions (see
     * {@link SessionAuthInterceptor}). Every bad-token case collapses to the same generic 400.
     * {@code @Transactional}: a failure after the claim rolls the claim back, so the link isn't burned.
     */
    @Transactional
    public void redeem(String token, String newPassword) {
        AuthService.requireValidPassword(newPassword);
        if (token == null || token.isBlank()) {
            throw invalidLink();
        }
        String hash = MachineKeyHasher.hash(token);
        PasswordResetToken row = tokenRepository.findByTokenHash(hash).orElseThrow(PasswordResetService::invalidLink);
        if (tokenRepository.tryClaim(hash, Instant.now()) != 1) {
            throw invalidLink();
        }
        Person person = personRepository.findById(row.getPersonId()).orElseThrow(PasswordResetService::invalidLink);
        person.setPasswordHash(passwordEncoder.encode(newPassword));
        person.setSessionsValidAfter(Instant.now());
        personRepository.save(person);
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid or expired reset link");
    }
}
