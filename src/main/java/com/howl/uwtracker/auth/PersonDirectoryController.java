package com.howl.uwtracker.auth;

import com.howl.uwtracker.auth.dto.PersonSummaryResponse;
import com.howl.uwtracker.repository.PersonRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backs the Run History "person" filter — a fixed, backend-provided set of choices (alias only, no
 * raw ids exposed to the user) rather than a free-text id input. Protected by SessionAuthInterceptor
 * (under /api/**), distinct from {@link AuthController}, which is scoped to the caller's own account.
 */
@RestController
@RequestMapping("/api/people")
public class PersonDirectoryController {

    private final PersonRepository personRepository;

    public PersonDirectoryController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @GetMapping
    public ResponseEntity<List<PersonSummaryResponse>> list() {
        List<PersonSummaryResponse> people = personRepository.findByAliasIsNotNullOrderByAliasAsc().stream()
                .map(PersonSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(people);
    }
}
