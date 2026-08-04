package com.howl.uwtracker.auth;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.SignupKey;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.SignupKeyRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_ALIAS_LENGTH = 64;

    private final PersonRepository personRepository;
    private final SignupKeyRepository signupKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(PersonRepository personRepository, SignupKeyRepository signupKeyRepository, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.signupKeyRepository = signupKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Signup is invite-gated: a single-use key, hashed the same way machine keys are
     * ({@link MachineKeyHasher}) since it's a high-entropy generated secret rather than a
     * user-chosen password. {@code @Transactional} so a failure between creating the person and
     * marking the key used can't leave one without the other.
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
        SignupKey key = signupKeyRepository.findByKeyHashAndUsedAtIsNull(MachineKeyHasher.hash(signupKey))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid or already-used signup key"));

        Person person = personRepository.save(new Person(username, passwordEncoder.encode(password)));
        key.markUsed(person);
        signupKeyRepository.save(key);
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
