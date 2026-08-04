package com.howl.uwtracker.leaderboards.dto;

import com.howl.uwtracker.domain.RunParticipant;

public record ParticipantSummary(String rawName, String characterName, String alias, String role) {

    public static ParticipantSummary from(RunParticipant participant) {
        String characterName = participant.getCharacter() == null ? null : participant.getCharacter().getCharacterName();
        String alias = participant.getCharacter() == null ? null : participant.getCharacter().getPerson().getAlias();
        return new ParticipantSummary(participant.getRawName(), characterName, alias, participant.getRole());
    }
}
