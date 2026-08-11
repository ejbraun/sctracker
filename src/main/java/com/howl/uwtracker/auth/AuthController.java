package com.howl.uwtracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.howl.uwtracker.auth.dto.LoginRequest;
import com.howl.uwtracker.auth.dto.PersonResponse;
import com.howl.uwtracker.auth.dto.SignupRequest;
import com.howl.uwtracker.auth.dto.UpdateAliasRequest;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PluginDllVersion;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.PluginDllVersionRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** specs/backend/03-auth.md. */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final PersonRepository personRepository;
    private final PluginDllVersionRepository pluginDllVersionRepository;

    public AuthController(AuthService authService, PersonRepository personRepository,
                           PluginDllVersionRepository pluginDllVersionRepository) {
        this.authService = authService;
        this.personRepository = personRepository;
        this.pluginDllVersionRepository = pluginDllVersionRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<PersonResponse> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        Person person = authService.signup(request.username(), request.password(), request.signupKey());
        startSession(httpRequest, person);
        return ResponseEntity.status(HttpStatus.CREATED).body(PersonResponse.from(person, newPluginVersionAvailable(person)));
    }

    @PostMapping("/login")
    public ResponseEntity<PersonResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Person person = authService.login(request.username(), request.password());
        startSession(httpRequest, person);
        return ResponseEntity.ok(PersonResponse.from(person, newPluginVersionAvailable(person)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/account/me")
    public ResponseEntity<PersonResponse> me(@CurrentPersonId Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated"));
        return ResponseEntity.ok(PersonResponse.from(person, newPluginVersionAvailable(person)));
    }

    @PatchMapping("/account/alias")
    public ResponseEntity<PersonResponse> updateAlias(@CurrentPersonId Long personId, @RequestBody UpdateAliasRequest request) {
        Person person = authService.updateAlias(personId, request.alias());
        return ResponseEntity.ok(PersonResponse.from(person, newPluginVersionAvailable(person)));
    }

    private void startSession(HttpServletRequest request, Person person) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.PERSON_ID, person.getId());
    }

    /**
     * True if this person has never recorded a plugin download at all (nothing to compare a
     * timestamp against, so treat it as needing the current build), or if their last download
     * predates the currently-detected dll build. See PluginDllVersionInitializer for how/when that
     * detection happens.
     */
    private boolean newPluginVersionAvailable(Person person) {
        Instant lastDownload = person.getLastPluginDownloadAt();
        if (lastDownload == null) {
            return true;
        }
        return pluginDllVersionRepository.findById(PluginDllVersion.SINGLETON_ID)
                .map(version -> lastDownload.isBefore(version.getDetectedAt()))
                .orElse(false);
    }
}
