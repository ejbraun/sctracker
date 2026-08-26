package com.howl.uwtracker.ingestion.dto;

import java.util.List;

/**
 * is_player/is_hero/is_henchman/deaths — not in the original spec draft, found in a real
 * GWToolboxdll payload sample: party slots can be AI-controlled heroes/henchmen, not just human
 * players, and each member reports how many times they died. All nullable here (defensive against
 * an older/different payload omitting them) — UploadRunWriter defaults isPlayer to true,
 * isHero/isHenchman to false, and deaths to 0 when absent.
 *
 * roleHint — set by the plugin for a Ranger/Assassin-primary member once they cast one of a fixed
 * set of trapping skills ("t1"/"t2"/"t3", lowercase); "unknown" (or null) until then or for any
 * other profession. Absent entirely on older plugin builds. See RoleDerivation for how this is
 * consumed — there's no positional fallback, so a member with no hint (or "unknown") stays
 * unresolved rather than defaulting to a T1/T2/T3 guess.
 *
 * itemDrops — always present on payloads that include it at all (empty list if nothing tracked
 * dropped for this member this run), one entry per tracked item that dropped, each with how many
 * times it was reserved for them. Measures loot *reservation* (GAME_SMSG_ITEM_UPDATE_OWNER), not
 * confirmed pickup. Nullable here anyway for the same older-plugin-build reason as the rest of this
 * record; UploadRunWriter treats a null/absent list the same as an empty one.
 *
 * rez_scroll_uses was removed (changelog 027): there was never an accurate way for the plugin to
 * track it client-side, so it was always sent as 0 — dead weight, not a real stat.
 *
 * gamblingStoneNet — how many Ghastly Summoning Stones this member won (positive) or lost
 * (negative) gambling with other party members at the end of a successful run. Unlike deaths,
 * null here is a meaningful value on its own (no gambling happened this run, or an older plugin
 * build that doesn't report it) rather than a stand-in for zero — UploadRunWriter stores it as-is
 * rather than defaulting a missing value to 0.
 */
public record PartyMemberDto(String name, Integer primary, Integer secondary,
                              Boolean isPlayer, Boolean isHero, Boolean isHenchman, Integer deaths,
                              String roleHint, List<ItemDropDto> itemDrops, Integer gamblingStoneNet) {
}
