package com.howl.uwtracker.characters;

import com.howl.uwtracker.characters.dto.CharacterResponse;
import com.howl.uwtracker.characters.dto.CharacterSummaryResponse;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.PlayerCharacterRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * specs/backend/04-characters.md.
 *
 * <p>DTO mapping happens inside these {@code @Transactional} methods rather than in the
 * controller — with {@code spring.jpa.open-in-view=false} (spec 01), entities returned past the
 * service boundary have no open Hibernate session behind them, so any lazy field a DTO factory
 * touches later would throw. {@link CharacterResponse#from} only reads {@code person.getId()}
 * today, which happens to be safe on an uninitialized proxy either way, but mapping inside the
 * transaction keeps that an implementation detail instead of a constraint on what the DTO's
 * allowed to contain.
 */
@Service
public class CharacterService {

    private final PlayerCharacterRepository characterRepository;
    private final PersonRepository personRepository;
    private final RunParticipantRepository runParticipantRepository;

    public CharacterService(PlayerCharacterRepository characterRepository, PersonRepository personRepository,
                             RunParticipantRepository runParticipantRepository) {
        this.characterRepository = characterRepository;
        this.personRepository = personRepository;
        this.runParticipantRepository = runParticipantRepository;
    }

    /** Every character owned by {@code personId}, name-sorted. Same view the owner and an admin see. */
    @Transactional(readOnly = true)
    public List<CharacterResponse> listForPerson(Long personId) {
        return characterRepository.findByPerson_IdOrderByCharacterNameAsc(personId).stream()
                .map(CharacterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CharacterSummaryResponse> listAll() {
        return characterRepository.findAllByOrderByCharacterNameAsc().stream()
                .map(CharacterSummaryResponse::from)
                .toList();
    }

    @Transactional
    public CharacterResponse add(Long personId, String characterName) {
        if (characterName == null || characterName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "character_name required");
        }
        if (characterRepository.existsByCharacterName(characterName)) {
            throw new ApiException(HttpStatus.CONFLICT, "character already registered");
        }
        return CharacterResponse.from(create(personId, characterName));
    }

    /**
     * Claims {@code characterName} for {@code personId} if no account has registered it yet — a
     * silent no-op when the name is blank or already taken (by this person or anyone else; this
     * never reassigns). Runs the same retroactive {@code run_participants} backfill as {@link #add}.
     *
     * <p>Used by {@code /upload-run} to auto-register the uploader's own character
     * ({@code party.character_name}) on their first upload, so a new guild member's runs count
     * without a separate website registration step. Returns whether it created a row.
     */
    @Transactional
    public boolean claimIfUnregistered(Long personId, String characterName) {
        if (characterName == null || characterName.isBlank()
                || characterRepository.existsByCharacterName(characterName)) {
            return false;
        }
        create(personId, characterName);
        return true;
    }

    /**
     * Bulk version of {@link #claimIfUnregistered} for {@code POST /sync-characters} — GW Launcher
     * Reforged's locally-detected character list. Same "claim if unclaimed, silent no-op otherwise"
     * rule per name (never reassigns one already taken, by this person or anyone else), just applied
     * to a whole list at once. Blank entries and in-list duplicates are dropped before processing;
     * order-preserving so the response can echo back exactly which names were newly claimed.
     *
     * @return the names that were actually newly registered, in submission order.
     */
    @Transactional
    public List<String> syncFromLauncher(Long personId, List<String> characterNames) {
        if (characterNames == null) {
            return List.of();
        }
        List<String> added = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : characterNames) {
            String name = raw == null ? null : raw.trim();
            if (name == null || name.isBlank() || !seen.add(name)) {
                continue;
            }
            if (claimIfUnregistered(personId, name)) {
                added.add(name);
            }
        }
        return added;
    }

    private PlayerCharacter create(Long personId, String characterName) {
        PlayerCharacter character = characterRepository.save(
                new PlayerCharacter(personRepository.getReferenceById(personId), characterName));

        // Retroactive backfill — specs/backend/04-characters.md: link this character to any past
        // run_participants rows ingested under this raw_name before the character existed.
        runParticipantRepository.backfillCharacter(character, characterName);

        return character;
    }

    @Transactional
    public void remove(Long personId, Long characterId) {
        PlayerCharacter character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "character not found"));
        if (!character.getPerson().getId().equals(personId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not your character");
        }
        characterRepository.delete(character);
    }
}
