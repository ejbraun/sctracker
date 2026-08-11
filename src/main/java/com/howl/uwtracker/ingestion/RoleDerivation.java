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
 * not-yet-resolved sentinel). There is no positional fallback. Indices 3-7 (or any T1-T3 index
 * left unresolved) fall through to an ORDERED (primary, secondary) combo match — flagged in
 * specs/backend/00-overview.md as an assumption unverified against real plugin data.
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

    private RoleDerivation() {
    }

    /**
     * @param partyMembers must have exactly 8 entries — caller validates size before calling
     *                     (party size != 8 is a 400 at the controller level, not this method's concern).
     * @return roles in the same order as partyMembers; entries are null where no combo matched.
     */
    public static List<String> resolveRoles(List<PartyMemberDto> partyMembers) {
        if (partyMembers.size() != 8) {
            throw new IllegalArgumentException("expected 8 party members, got " + partyMembers.size());
        }
        String[] roles = new String[8];

        // Pass 1: apply plugin-supplied hints wherever that member actually sits in the array.
        Set<String> claimedLabels = new LinkedHashSet<>();
        for (int i = 0; i < 8; i++) {
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
