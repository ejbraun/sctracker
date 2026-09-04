package com.howl.uwtracker.auth;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.SignupKey;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.SignupKeyRepository;
import com.howl.uwtracker.repository.SignupLinkRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_ALIAS_LENGTH = 64;

    private final PersonRepository personRepository;
    private final SignupKeyRepository signupKeyRepository;
    private final SignupLinkRepository signupLinkRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(PersonRepository personRepository, SignupKeyRepository signupKeyRepository,
                        SignupLinkRepository signupLinkRepository, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.signupKeyRepository = signupKeyRepository;
        this.signupLinkRepository = signupLinkRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Signup is invite-gated. The {@code signupKey} field accepts either flavour of invite, hashed
     * the same way machine keys are ({@link MachineKeyHasher} — a high-entropy generated secret, not
     * a user-chosen password):
     * <ul>
     *   <li>a single-use {@link SignupKey} (marked used + linked to the new person), or</li>
     *   <li>a multi-use {@code signup_links} token — redeemed by an atomic
     *       {@code use_count = use_count + 1 WHERE use_count < max_uses AND revoked_at IS NULL}, so
     *       an N-use link can't be pushed past N by concurrent signups.</li>
     * </ul>
     * Both misses collapse to the same generic {@code 400}. {@code @Transactional} so a later
     * failure (e.g. a username race) rolls back the person insert <em>and</em> the redemption.
     */
    @Transactional
    public Person signup(String username, String password, String signupKey) {
        if (username == null || username.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "username required");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (signupKey == null || signupKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "signup key required");
        }
        if (personRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "username taken");
        }

        String hash = MachineKeyHasher.hash(signupKey);
        Optional<SignupKey> singleUseKey = signupKeyRepository.findByKeyHashAndUsedAtIsNull(hash);

        Person person;
        if (singleUseKey.isPresent()) {
            person = personRepository.save(new Person(username, passwordEncoder.encode(password)));
            singleUseKey.get().markUsed(person);
            signupKeyRepository.save(singleUseKey.get());
        } else if (signupLinkRepository.tryClaim(hash) == 1) {
            person = personRepository.save(new Person(username, passwordEncoder.encode(password)));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid or already-used signup key");
        }
        return person;
    }

    public Person login(String username, String password) {
        // Same "invalid credentials" message whether the username doesn't exist or the password
        // is wrong — don't leak which, per specs/backend/03-auth.md.
        Person person = personRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        if (!passwordEncoder.matches(password, person.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return person;
    }

    /** {@code rawAlias} null/blank clears a previously-set alias. */
    public Person updateAlias(Long personId, String rawAlias) {
        String alias = (rawAlias == null || rawAlias.isBlank()) ? null : rawAlias.trim();
        if (alias != null && alias.length() > MAX_ALIAS_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "alias must be at most " + MAX_ALIAS_LENGTH + " characters");
        }
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated"));
        if (alias != null && !alias.equals(person.getAlias()) && personRepository.existsByAlias(alias)) {
            throw new ApiException(HttpStatus.CONFLICT, "alias taken");
        }
        person.setAlias(alias);
        return personRepository.save(person);
    }
}
