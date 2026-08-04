package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleDerivationTest {

    private static final int WARRIOR = 1;
    private static final int RANGER = 2;
    private static final int MONK = 3;
    private static final int NECROMANCER = 4;
    private static final int MESMER = 5;
    private static final int ELEMENTALIST = 6;
    private static final int ASSASSIN = 7;
    private static final int RITUALIST = 8;
    private static final int DERVISH = 10;

    private static PartyMemberDto member(String name, int primary, int secondary) {
        return new PartyMemberDto(name, primary, secondary, true, false, false, 0);
    }

    /** T1/T2/T3 (Ranger/Assassin) + T4/LT/emo/spiker/sos in party order 3-7. */
    private static List<PartyMemberDto> fullValidParty() {
        return List.of(
                member("T1", RANGER, ASSASSIN),
                member("T2", RANGER, ASSASSIN),
                member("T3", RANGER, ASSASSIN),
                member("T4", ELEMENTALIST, MESMER),
                member("LT", MESMER, ASSASSIN),
                member("Spiker", DERVISH, WARRIOR),
                member("SoS", RITUALIST, RANGER),
                member("Emo", ELEMENTALIST, MONK)
        );
    }

    @Test
    void positionalRolesForFirstThreeSlots() {
        List<String> roles = RoleDerivation.resolveRoles(fullValidParty());
        assertEquals("T1", roles.get(0));
        assertEquals("T2", roles.get(1));
        assertEquals("T3", roles.get(2));
    }

    @Test
    void t4IsElementalistMesmer() {
        List<String> roles = RoleDerivation.resolveRoles(fullValidParty());
        assertEquals("T4", roles.get(3));
    }

    @Test
    void ltIsMesmerAssassin() {
        List<String> roles = RoleDerivation.resolveRoles(fullValidParty());
        assertEquals("LT", roles.get(4));
    }

    @Test
    void spikerIsDervishPrimaryRegardlessOfSecondary() {
        List<String> roles = RoleDerivation.resolveRoles(fullValidParty());
        assertEquals("spiker", roles.get(5));
    }

    @Test
    void spikerIsAlsoMesmerRanger() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(5, member("Spiker", MESMER, RANGER));
        List<String> roles = RoleDerivation.resolveRoles(party);
        assertEquals("spiker", roles.get(5));
    }

    @Test
    void sosIsRitualistRanger() {
        List<String> roles = RoleDerivation.resolveRoles(fullValidParty());
        assertEquals("sos", roles.get(6));
    }

    @Test
    void sosIsAlsoNecromancerRanger() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(6, member("SoS", NECROMANCER, RANGER));
        List<String> roles = RoleDerivation.resolveRoles(party);
        assertEquals("sos", roles.get(6));
    }

    @Test
    void emoIsElementalistMonk() {
        List<String> roles = RoleDerivation.resolveRoles(fullValidParty());
        assertEquals("emo", roles.get(7));
    }

    @Test
    void unmatchedComboResolvesToNullNotAnException() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(3, member("Mystery", WARRIOR, MONK));
        List<String> roles = RoleDerivation.resolveRoles(party);
        assertNull(roles.get(3));
    }

    @Test
    void rejectsPartySizeOtherThanEight() {
        List<PartyMemberDto> tooFew = List.of(member("Solo", RANGER, ASSASSIN));
        assertThrows(IllegalArgumentException.class, () -> RoleDerivation.resolveRoles(tooFew));
    }

    @Test
    void reversedComboDoesNotMatchOrderedMapping() {
        // Mesmer/Elementalist (reversed T4) should NOT resolve to T4 under the ordered interpretation.
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(3, member("Reversed", MESMER, ELEMENTALIST));
        List<String> roles = RoleDerivation.resolveRoles(party);
        assertNull(roles.get(3));
    }
}
