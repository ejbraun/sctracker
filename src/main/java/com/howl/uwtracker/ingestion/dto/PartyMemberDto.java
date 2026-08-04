package com.howl.uwtracker.ingestion.dto;

/**
 * is_player/is_hero/is_henchman/deaths — not in the original spec draft, found in a real
 * GWToolboxdll payload sample: party slots can be AI-controlled heroes/henchmen, not just human
 * players, and each member reports how many times they died. All nullable here (defensive against
 * an older/different payload omitting them) — UploadRunWriter defaults isPlayer to true,
 * isHero/isHenchman to false, and deaths to 0 when absent.
 *
 * roleHint — set by the plugin for a Ranger/Assassin-primary member once they cast one of a fixed
 * set of trapping skills ("t1"/"t2"/"t3", lowercase); null until then or for any other profession.
 * Absent entirely on older plugin builds. See RoleDerivation for how this is consumed.
 */
public record PartyMemberDto(String name, Integer primary, Integer secondary,
                              Boolean isPlayer, Boolean isHero, Boolean isHenchman, Integer deaths,
                              String roleHint) {
}
