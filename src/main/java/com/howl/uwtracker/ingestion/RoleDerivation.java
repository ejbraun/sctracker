package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure role-derivation algorithm from specs/backend/02-ingestion-upload-run.md. Deliberately
 * isolated from persistence/HTTP concerns so it's directly unit-testable against fixture arrays.
 *
 * T1/T2/T3 share the same Ranger/Assassin combo, so profession alone can't distinguish them.
 * The plugin's optional per-member role_hint ("t1"/"t2"/"t3", set once that player casts one of a
 * fixed set of trapping skills) is the only way to resolve this — a member stays unresolved
 * (null role) if it has no role_hint, or if role_hint is "unknown" (the plugin's explicit
 * not-yet-resolved sentinel). There is no positional fallback for T2/T3 (or the general case).
 * T1 is the exception: it's inferred by elimination once T2 and T3 are both hinted, rather than
 * waiting on its own hint — deliberately not derived from any skill cast. Indices 3-7 (or any
 * T1-T3 slot left unresolved) fall through to an ORDERED (primary, secondary) combo match —
 * flagged in specs/backend/00-overview.md as an assumption unverified against real plugin data.
 *
 * As of the multi-upload role_hint reconciliation change, the plugin only ever populates
 * role_hint/role_skills for the uploader's own character — observing another real player's skill
 * casts only works within the local client's compass/network range, which isn't guaranteed for a
 * spread-out party. {@link #restrictHintsToSelf} enforces server-side that only that self-reported
 * hint is trusted from any single upload; see {@link com.howl.uwtracker.ingestion.UploadRunWriter}
 * for how the writer merges self-reports across multiple uploads of the same run (never letting an
 * upload with no data for a member erase an earlier upload's self-report) and infers the last of
 * T1/T2/T3 by elimination once two of the three are known from accumulated DB state — that part no
 * longer happens within a single call to {@link #resolveRoles} here, since at most one member per
 * upload can have a real hint now.
 */
public final class RoleDerivation {

    private static final Logger log = LoggerFactory.getLogger(RoleDerivation.class);

    private static final int WARRIOR = 1;
    private static final int RANGER = 2;
    private static final int MONK = 3;
    private static final int NECROMANCER = 4;
    private static final int MESMER = 5;
    private static final int ELEMENTALIST = 6;
    private static final int ASSASSIN = 7;
    private static final int RITUALIST = 8;
    private static final int PARAGON = 9;
    private static final int DERVISH = 10;

    private static final List<String> TRAPPER_LABELS = List.of("T1", "T2", "T3");

    // Elvar Slayer's client never reliably reports a role_hint, but he only ever plays T2 — an
    // identity-based override applied before any role_hint is read, so it wins regardless of who
    // uploaded the run (restrictHintsToSelf would otherwise null out his hint on runs he didn't
    // upload) and regardless of whatever hint value his own client happens to send.
    private static final String ELVAR_SLAYER = "Elvar Slayer";

    private RoleDerivation() {
    }

    /**
     * Clears {@code role_hint} on every member except {@code selfName} (the uploader's own
     * character, from {@code PartyDto.characterName}) before {@link #resolveRoles} sees the list —
     * the only server-side enforcement that a hint is actually self-reported, not an old/buggy
     * client's unreliable guess at someone else's role. {@code String.equals} against a null/
     * unmatched {@code selfName} (missing or stale payload) returns false for every member, so that
     * degrades safely to "trust nobody's hint" rather than needing a separate null case.
     */
    public static List<PartyMemberDto> restrictHintsToSelf(String selfName, List<PartyMemberDto> members) {
        return members.stream()
                .map(m -> m.name().equals(selfName) ? m : clearHint(m))
                .toList();
    }

    private static PartyMemberDto clearHint(PartyMemberDto m) {
        return new PartyMemberDto(m.name(), m.primary(), m.secondary(), m.isPlayer(), m.isHero(), m.isHenchman(),
                m.deaths(), null, m.itemDrops(), m.gamblingStoneNet());
    }

    /**
     * @param partyMembers must have exactly 8 entries — caller validates size before calling
     *                     (party size != 8 is a 400 at the controller level, not this method's concern).
     * @return roles in the same order as partyMembers; entries are null where no combo matched.
     *         T1 is assigned by elimination (not its own hint) once T2 and T3 are both hinted and
     *         exactly one Ranger/Assassin member remains unassigned.
     */
    public static List<String> resolveRoles(List<PartyMemberDto> partyMembers) {
        if (partyMembers.size() != 8) {
            throw new IllegalArgumentException("expected 8 party members, got " + partyMembers.size());
        }
        String[] roles = new String[8];
        Set<String> claimedLabels = new LinkedHashSet<>();

        // Pass 0: Elvar Slayer is always T2, ahead of anything role_hint-based below.
        for (int i = 0; i < 8; i++) {
            if (ELVAR_SLAYER.equals(partyMembers.get(i).name())) {
                roles[i] = "T2";
                claimedLabels.add("T2");
                break;
            }
        }

        // Pass 1: apply plugin-supplied hints wherever that member actually sits in the array.
        for (int i = 0; i < 8; i++) {
            if (roles[i] != null) {
                continue;
            }
            String hint = partyMembers.get(i).roleHint();
            if (hint == null || hint.equalsIgnoreCase("unknown")) {
                continue;
            }
            String normalized = hint.toUpperCase(Locale.ROOT);
            if (!TRAPPER_LABELS.contains(normalized) || !claimedLabels.add(normalized)) {
                log.warn("ignoring invalid/duplicate role_hint '{}' at party index {}", hint, i);
                continue;
            }
            roles[i] = normalized;
        }

        // Pass 1.5: T1 never gets its own hint from skills — infer it by elimination once T2 and T3
        // are both hinted, from whichever Ranger/Assassin member is still unassigned. Only fires
        // when exactly one such member remains; otherwise there's nothing safe to conclude.
        if (claimedLabels.contains("T2") && claimedLabels.contains("T3") && !claimedLabels.contains("T1")) {
            Integer onlyCandidate = null;
            boolean singleCandidate = true;
            for (int i = 0; i < 8; i++) {
                PartyMemberDto member = partyMembers.get(i);
                if (roles[i] == null && member.primary() == RANGER && member.secondary() == ASSASSIN) {
                    if (onlyCandidate != null) {
                        singleCandidate = false;
                        break;
                    }
                    onlyCandidate = i;
                }
            }
            if (singleCandidate && onlyCandidate != null) {
                roles[onlyCandidate] = "T1";
            }
        }

        // Pass 2: everything still unassigned (normally indices 3-7; T1-T3 slots with no valid hint
        // stay null here too, since Ranger/Assassin has no profession-combo match) resolves by combo.
        for (int i = 0; i < 8; i++) {
            if (roles[i] == null) {
                roles[i] = resolveByProfessionCombo(partyMembers.get(i));
            }
        }
        return Arrays.asList(roles);
    }

    private static String resolveByProfessionCombo(PartyMemberDto member) {
        int primary = member.primary();
        int secondary = member.secondary();

        if (primary == MESMER && secondary == ELEMENTALIST) {
            return "T4";
        }
        if (primary == MESMER && secondary == ASSASSIN) {
            return "LT";
        }
        if (primary == ELEMENTALIST && secondary == MONK) {
            return "Emo";
        }
        if (primary == DERVISH) {
            return "Derv";
        }
        if (primary == MESMER && secondary == RANGER) {
            return "Spiker";
        }
        if (primary == RITUALIST && secondary == RANGER) {
            return "SoS";
        }
        if (primary == NECROMANCER && secondary == RANGER) {
            return "Necro";
        }
        if (primary == RANGER && secondary == NECROMANCER) {
            return "RangerNecro";
        }
        return null;
    }
}
