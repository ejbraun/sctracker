package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.AdminUserModuleResponse;
import com.howl.uwtracker.admin.dto.AdminUserResponse;
import com.howl.uwtracker.characters.CharacterService;
import com.howl.uwtracker.characters.dto.CharacterResponse;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.modules.ModuleGrantService;
import com.howl.uwtracker.repository.AdminRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.Comparator;

@Service
public class AdminUserService {

    private final PersonRepository personRepository;
    private final AdminRepository adminRepository;
    private final CharacterService characterService;
    private final ModuleGrantService moduleGrantService;

    public AdminUserService(PersonRepository personRepository, AdminRepository adminRepository,
                             CharacterService characterService, ModuleGrantService moduleGrantService) {
        this.personRepository = personRepository;
        this.adminRepository = adminRepository;
        this.characterService = characterService;
        this.moduleGrantService = moduleGrantService;
    }

    public List<AdminUserResponse> list() {
        Set<Long> adminPersonIds = adminRepository.findAllPersonIds();
        return personRepository.findAll().stream()
                .sorted(Comparator.comparing(Person::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(person -> AdminUserResponse.from(person, adminPersonIds.contains(person.getId())))
                .toList();
    }

    public AdminUserResponse setCanReportFailures(Long personId, boolean canReportFailures) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        person.setCanReportFailures(canReportFailures);
        personRepository.save(person);
        return AdminUserResponse.from(person, adminRepository.existsById(personId));
    }

    /** The target user's registered characters — same view {@code GET /api/characters} gives that user. */
    public List<CharacterResponse> listCharacters(Long personId) {
        requireUser(personId);
        return characterService.listForPerson(personId);
    }

    /**
     * Registers a character to the target user, with the same rules and retroactive backfill as the
     * self-serve {@code POST /api/characters} — blank name → 400, a name already registered to
     * anyone → 409 (character names are globally unique).
     */
    public CharacterResponse addCharacter(Long personId, String characterName) {
        requireUser(personId);
        return characterService.add(personId, characterName);
    }

    /** The per-user module checklist — every enabled module with this user's grant state. */
    public List<AdminUserModuleResponse> listModules(Long personId) {
        requireUser(personId);
        return moduleGrantService.listForUser(personId);
    }

    public void grantModule(Long personId, String moduleKey, Long grantedByPersonId) {
        requireUser(personId);
        moduleGrantService.grant(personId, moduleKey, grantedByPersonId);
    }

    public void revokeModule(Long personId, String moduleKey) {
        requireUser(personId);
        moduleGrantService.revoke(personId, moduleKey);
    }

    private void requireUser(Long personId) {
        if (!personRepository.existsById(personId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "user not found");
        }
    }
}
