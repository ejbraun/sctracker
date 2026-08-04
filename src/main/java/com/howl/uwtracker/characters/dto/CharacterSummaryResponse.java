package com.howl.uwtracker.characters.dto;

import com.howl.uwtracker.domain.PlayerCharacter;

/**
 * Minimal view of a character — backs the Run History "character" filter dropdown. {@code personId}
 * is included (unlike a raw username, never exposed) so the frontend can cross-filter the "person"
 * and "character" dropdowns against each other without a round trip per keystroke.
 */
public record CharacterSummaryResponse(Long id, String characterName, Long personId) {

    public static CharacterSummaryResponse from(PlayerCharacter character) {
        return new CharacterSummaryResponse(character.getId(), character.getCharacterName(), character.getPerson().getId());
    }
}
