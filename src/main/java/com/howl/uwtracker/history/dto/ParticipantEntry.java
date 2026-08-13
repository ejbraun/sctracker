package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.RunParticipant;

public record ParticipantEntry(Integer partyIndex, String rawName, Long characterId, String characterName,
                                String primaryProfession, String secondaryProfession, String role,
                                boolean isPlayer, boolean isHero, boolean isHenchman, Integer deaths) {

    public static ParticipantEntry from(RunParticipant rp) {
        Long characterId = rp.getCharacter() == null ? null : rp.getCharacter().getId();
        String characterName = rp.getCharacter() == null ? null : rp.getCharacter().getCharacterName();
        String secondaryProfession = rp.getSecondaryProfession() == null ? null : rp.getSecondaryProfession().getName();
        return new ParticipantEntry(rp.getPartyIndex(), rp.getRawName(), characterId, characterName,
                rp.getPrimaryProfession().getName(), secondaryProfession, rp.getRole(),
                rp.isPlayer(), rp.isHero(), rp.isHenchman(), rp.getDeaths());
    }
}
