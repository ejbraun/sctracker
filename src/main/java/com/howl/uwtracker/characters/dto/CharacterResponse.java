package com.howl.uwtracker.characters.dto;

import com.howl.uwtracker.domain.PlayerCharacter;

public record CharacterResponse(Long id, String characterName, Long personId) {

    public static CharacterResponse from(PlayerCharacter character) {
        return new CharacterResponse(character.getId(), character.getCharacterName(), character.getPerson().getId());
    }
}
