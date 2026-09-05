package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.AdminUserModuleResponse;
import com.howl.uwtracker.admin.dto.AdminUserResponse;
import com.howl.uwtracker.characters.CharacterService;
import com.howl.uwtracker.characters.dto.CharacterResponse;
import com.howl.uwtracker.domain.Admin;
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

    /**
     * Adds/removes the target's {@code admins} row (idempotent). An admin can't revoke their own
     * access — that's the guard against locking every admin out; there's always at least one left.
     */
    public AdminUserResponse setAdmin(Long personId, boolean makeAdmin, Long actingAdminPersonId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        if (!makeAdmin && personId.equals(actingAdminPersonId)) {
            throw new ApiException(HttpStatus.CONFLICT, "you can't revoke your own admin access");
        }
        boolean isAdmin = adminRepository.existsById(personId);
        if (makeAdmin && !isAdmin) {
            adminRepository.save(new Admin(personId));
        } else if (!makeAdmin && isAdmin) {
            adminRepository.deleteById(personId);
        }
        return AdminUserResponse.from(person, makeAdmin);
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

    /**
     * Hard-deletes a user account. Every dependent row is cleaned up at the DB level, not here —
     * {@code characters}, {@code machine_keys}, {@code admins}, and {@code person_module_grants}
     * (as grantee) all have {@code ON DELETE CASCADE} to {@code people}; run history, reports/awards
     * attribution, {@code granted_by}, and signup-link {@code created_by} all {@code ON DELETE SET
     * NULL} instead, so past runs and admin-trail records survive with the person reference cleared
     * (same "unlinked, not deleted" posture as removing a single character). Same self-protection as
     * {@link #setAdmin} — you can't delete your own account, which incidentally also means an admin
     * can never delete the last admin (themselves).
     */
    public void delete(Long personId, Long actingAdminPersonId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        if (personId.equals(actingAdminPersonId)) {
            throw new ApiException(HttpStatus.CONFLICT, "you can't delete your own account");
        }
        personRepository.delete(person);
    }

    private void requireUser(Long personId) {
        if (!personRepository.existsById(personId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "user not found");
        }
    }
}
