package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.ingestion.dto.PartyMemberDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure role-derivation algorithm from specs/backend/02-ingestion-upload-run.md. Deliberately
 * isolated from persistence/HTTP concerns so it's directly unit-testable against fixture arrays.
 *
 * Indices 0-2 are positional (T1/T2/T3 share the same Ranger/Assassin combo, so profession alone
 * can't distinguish them). Indices 3-7 are resolved by an ORDERED (primary, secondary) combo match
 * — flagged in specs/backend/00-overview.md as an assumption unverified against real plugin data.
 */
public final class RoleDerivation {

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
        List<String> roles = new ArrayList<>(8);
        roles.add("T1");
        roles.add("T2");
        roles.add("T3");
        for (int i = 3; i < 8; i++) {
            roles.add(resolveByProfessionCombo(partyMembers.get(i)));
        }
        return roles;
    }

    private static String resolveByProfessionCombo(PartyMemberDto member) {
        int primary = member.primary();
        int secondary = member.secondary();

        if (primary == ELEMENTALIST && secondary == MESMER) {
            return "T4";
        }
        if (primary == MESMER && secondary == ASSASSIN) {
            return "LT";
        }
        if (primary == ELEMENTALIST && secondary == MONK) {
            return "emo";
        }
        if (primary == DERVISH || (primary == MESMER && secondary == RANGER)) {
            return "spiker";
        }
        if ((primary == RITUALIST || primary == NECROMANCER) && secondary == RANGER) {
            return "sos";
        }
        return null;
    }
}
