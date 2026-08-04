package com.howl.uwtracker.ingestion.dto;

import java.util.List;

public record PartyDto(
        Long utcStart,
        Integer mapId,
        String characterName,
        String endReason,
        List<PartyMemberDto> partyMembers
) {
}
