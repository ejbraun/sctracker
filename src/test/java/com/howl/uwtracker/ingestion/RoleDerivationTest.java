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
        return new PartyMemberDto(name, primary, secondary, true, false, false, 0, null, List.of(), null);
    }

    private static PartyMemberDto member(String name, int primary, int secondary, String roleHint) {
        return new PartyMemberDto(name, primary, secondary, true, false, false, 0, roleHint, List.of(), null);
    }

    /**
     * Indices 0-2 are unhinted Ranger/Assassin (named "T1"/"T2"/"T3" for readability only — with
     * no role_hint they resolve to null, per noHintLeavesTrapperRolesNull) + T4/LT/Spiker/SoS/Emo
     * in party order 3-7.
     */
    private static List<PartyMemberDto> fullValidParty() {
        return List.of(
                member("T1", RANGER, ASSASSIN),
                member("T2", RANGER, ASSASSIN),
                member("T3", RANGER, ASSASSIN),
                member("T4", MESMER, ELEMENTALIST),
                member("LT", MESMER, ASSASSIN),
                member("Spiker", DERVISH, WARRIOR),
                member("SoS", RITUALIST, RANGER),
                member("Emo", ELEMENTALIST, MONK)
        );
    }

    @Test
    void noHintLeavesTrapperRolesNull() {
        List<String> roles = RoleDerivation.resolveByTrapperModel(fullValidParty());
        assertNull(roles.get(0));
        assertNull(roles.get(1));
        assertNull(roles.get(2));
    }

    @Test
    void t4IsMesmerElementalist() {
        List<String> roles = RoleDerivation.resolveByTrapperModel(fullValidParty());
        assertEquals("T4", roles.get(3));
    }

    @Test
    void ltIsMesmerAssassin() {
        List<String> roles = RoleDerivation.resolveByTrapperModel(fullValidParty());
        assertEquals("LT", roles.get(4));
    }

    @Test
    void dervIsDervishPrimaryRegardlessOfSecondary() {
        List<String> roles = RoleDerivation.resolveByTrapperModel(fullValidParty());
        assertEquals("Derv", roles.get(5));
    }

    @Test
    void spikerIsMesmerRanger() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(5, member("Spiker", MESMER, RANGER));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("Spiker", roles.get(5));
    }

    @Test
    void sosIsRitualistRanger() {
        List<String> roles = RoleDerivation.resolveByTrapperModel(fullValidParty());
        assertEquals("SoS", roles.get(6));
    }

    @Test
    void necroIsNecromancerRanger() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(6, member("Necro", NECROMANCER, RANGER));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("Necro", roles.get(6));
    }

    @Test
    void rangerNecroIsRangerNecromancer() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(6, member("RangerNecro", RANGER, NECROMANCER));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("RangerNecro", roles.get(6));
    }

    @Test
    void emoIsElementalistMonk() {
        List<String> roles = RoleDerivation.resolveByTrapperModel(fullValidParty());
        assertEquals("Emo", roles.get(7));
    }

    @Test
    void unmatchedComboResolvesToNullNotAnException() {
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(3, member("Mystery", WARRIOR, MONK));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(3));
    }

    @Test
    void rejectsPartySizeOtherThanEight() {
        List<PartyMemberDto> tooFew = List.of(member("Solo", RANGER, ASSASSIN));
        assertThrows(IllegalArgumentException.class, () -> RoleDerivation.resolveByTrapperModel(tooFew));
    }

    @Test
    void reversedComboDoesNotMatchOrderedMapping() {
        // Elementalist/Mesmer (reversed T4) should NOT resolve to T4 under the ordered interpretation.
        List<PartyMemberDto> party = fullValidParty();
        party = new java.util.ArrayList<>(party);
        party.set(3, member("Reversed", ELEMENTALIST, MESMER));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(3));
    }

    @Test
    void allThreeHintedInArrayOrderResolveToTheirLabels() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "t1"));
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        party.set(2, member("T3", RANGER, ASSASSIN, "t3"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T1", roles.get(0));
        assertEquals("T2", roles.get(1));
        assertEquals("T3", roles.get(2));
    }

    @Test
    void hintsOverridePositionWhenOutOfOrder() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("ActuallyT2", RANGER, ASSASSIN, "t2"));
        party.set(1, member("ActuallyT1", RANGER, ASSASSIN, "t1"));
        party.set(2, member("ActuallyT3", RANGER, ASSASSIN, "t3"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(0));
        assertEquals("T1", roles.get(1));
        assertEquals("T3", roles.get(2));
    }

    @Test
    void unhintedTrapperSlotsStayNullEvenWhenOthersAreHinted() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "t1"));
        // indices 1 and 2 left un-hinted (null roleHint, via fullValidParty()'s plain members)
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T1", roles.get(0));
        assertNull(roles.get(1));
        assertNull(roles.get(2));
    }

    @Test
    void hintIsCaseNormalizedToUppercase() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(1));
    }

    @Test
    void duplicateHintFirstWinsSecondStaysNull() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("FirstT1", RANGER, ASSASSIN, "t1"));
        party.set(1, member("AlsoClaimsT1", RANGER, ASSASSIN, "t1"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T1", roles.get(0));
        // index 1's duplicate hint is ignored, and with no positional fallback it stays unresolved.
        assertNull(roles.get(1));
        assertNull(roles.get(2));
    }

    @Test
    void invalidHintValueLeavesRoleNull() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "t9"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(0));
        assertNull(roles.get(1));
        assertNull(roles.get(2));
    }

    @Test
    void unknownHintLeavesRoleNull() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "unknown"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(0));
    }

    @Test
    void unknownHintIsCaseInsensitive() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "UNKNOWN"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(0));
    }

    @Test
    void t2AndT3HintedInferTheRemainingTrapperAsT1() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        party.set(2, member("T3", RANGER, ASSASSIN, "t3"));
        // index 0 gets no role_hint at all — inferred as T1 purely by elimination.
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T1", roles.get(0));
        assertEquals("T2", roles.get(1));
        assertEquals("T3", roles.get(2));
    }

    @Test
    void t2AndT3HintedInferT1RegardlessOfWhichSlotIsUnhinted() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T3", RANGER, ASSASSIN, "t3"));
        party.set(2, member("T2", RANGER, ASSASSIN, "t2"));
        // index 1, out of position, is the unhinted one and still resolves to T1.
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T3", roles.get(0));
        assertEquals("T1", roles.get(1));
        assertEquals("T2", roles.get(2));
    }

    @Test
    void t1EliminationWorksWhenTrapperTrioIsNotAtIndicesZeroToTwo() {
        // The Ranger/Assassin trio can land anywhere in the array — build a party where they sit
        // at indices 2, 5, and 7 instead of the conventional 0-2, interleaved with other combos.
        List<PartyMemberDto> party = List.of(
                member("T4", MESMER, ELEMENTALIST),
                member("LT", MESMER, ASSASSIN),
                member("T2", RANGER, ASSASSIN, "t2"),
                member("SoS", RITUALIST, RANGER),
                member("Emo", ELEMENTALIST, MONK),
                member("T3", RANGER, ASSASSIN, "t3"),
                member("Spiker", DERVISH, WARRIOR),
                member("T1", RANGER, ASSASSIN)
        );
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(2));
        assertEquals("T3", roles.get(5));
        assertEquals("T1", roles.get(7));
    }

    @Test
    void t1EliminationDoesNotFireWithOnlyOneOfT2OrT3Hinted() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        // index 2 (T3) left unhinted, so elimination must not guess index 0 or 2.
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(0));
        assertEquals("T2", roles.get(1));
        assertNull(roles.get(2));
    }

    @Test
    void t1EliminationDoesNotOverrideAnExplicitT1Hint() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "t1"));
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        party.set(2, member("T3", RANGER, ASSASSIN, "t3"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T1", roles.get(0));
        assertEquals("T2", roles.get(1));
        assertEquals("T3", roles.get(2));
    }

    @Test
    void t1EliminationSkipsWhenMoreThanOneCandidateRemains() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        party.set(2, member("T3", RANGER, ASSASSIN, "t3"));
        // A fourth, unhinted Ranger/Assassin member (e.g. a henchman filling a trapper slot)
        // makes elimination ambiguous, so nobody gets guessed as T1.
        party.set(3, member("ExtraRA", RANGER, ASSASSIN));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertNull(roles.get(0));
        assertEquals("T2", roles.get(1));
        assertEquals("T3", roles.get(2));
        assertNull(roles.get(3));
    }

    @Test
    void restrictHintsToSelfPreservesTheSelfEntrysHint() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        List<PartyMemberDto> restricted = RoleDerivation.restrictHintsToSelf("T2", party);
        assertEquals("t2", restricted.get(1).roleHint());
    }

    @Test
    void restrictHintsToSelfClearsEveryoneElsesHint() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("T1", RANGER, ASSASSIN, "t1"));
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        party.set(2, member("T3", RANGER, ASSASSIN, "t3"));
        // Only "T2" is self — T1's and T3's hints are stray/unreliable (e.g. an old client's
        // range-limited guess at another player) and must be dropped before role resolution.
        List<PartyMemberDto> restricted = RoleDerivation.restrictHintsToSelf("T2", party);
        assertNull(restricted.get(0).roleHint());
        assertEquals("t2", restricted.get(1).roleHint());
        assertNull(restricted.get(2).roleHint());
    }

    @Test
    void restrictHintsToSelfClearsEveryoneWhenSelfNameMatchesNoMember() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        List<PartyMemberDto> restricted = RoleDerivation.restrictHintsToSelf("NobodyByThisName", party);
        assertNull(restricted.get(1).roleHint());
    }

    @Test
    void restrictHintsToSelfClearsEveryoneWhenSelfNameIsNull() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2"));
        List<PartyMemberDto> restricted = RoleDerivation.restrictHintsToSelf(null, party);
        assertNull(restricted.get(1).roleHint());
    }

    @Test
    void restrictHintsToSelfThenResolveRolesIgnoresAStrayNonSelfHint() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(1, member("T2", RANGER, ASSASSIN, "t2")); // self, trustworthy
        party.set(2, member("T3", RANGER, ASSASSIN, "t3")); // stray hint from someone else's upload
        List<String> roles = RoleDerivation.resolveByTrapperModel(RoleDerivation.restrictHintsToSelf("T2", party));
        assertNull(roles.get(0));
        assertEquals("T2", roles.get(1));
        // T3's hint was cleared (not self), so it stays unresolved rather than being trusted —
        // and with only one real hint present, elimination can't fire either.
        assertNull(roles.get(2));
    }

    @Test
    void elvarSlayerIsAlwaysT2EvenWithNoHint() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("Elvar Slayer", RANGER, ASSASSIN));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(0));
    }

    @Test
    void elvarSlayerOverridesHisOwnConflictingHint() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("Elvar Slayer", RANGER, ASSASSIN, "t1"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(0));
    }

    @Test
    void elvarSlayerBlocksAnotherMemberFromClaimingT2() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("Elvar Slayer", RANGER, ASSASSIN));
        party.set(1, member("AlsoClaimsT2", RANGER, ASSASSIN, "t2"));
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(0));
        // The real hint loses to the identity override and, with no positional fallback, stays unresolved.
        assertNull(roles.get(1));
    }

    @Test
    void elvarSlayerStillLetsT1EliminationFireOffHisPreClaimedT2() {
        List<PartyMemberDto> party = new java.util.ArrayList<>(fullValidParty());
        party.set(0, member("Elvar Slayer", RANGER, ASSASSIN));
        party.set(2, member("T3", RANGER, ASSASSIN, "t3"));
        // index 1 gets no hint at all — inferred as T1 purely by elimination against Elvar's T2.
        List<String> roles = RoleDerivation.resolveByTrapperModel(party);
        assertEquals("T2", roles.get(0));
        assertEquals("T1", roles.get(1));
        assertEquals("T3", roles.get(2));
    }
}
