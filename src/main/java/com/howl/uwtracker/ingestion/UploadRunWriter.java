package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.RoleModel;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.domain.RunParticipantItemDrop;
import com.howl.uwtracker.domain.RunParticipantItemDropId;
import com.howl.uwtracker.ingestion.dto.ItemDropDto;
import com.howl.uwtracker.ingestion.dto.ObjectiveDto;
import com.howl.uwtracker.ingestion.dto.ObjectiveSectionDto;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.PlayerCharacterRepository;
import com.howl.uwtracker.repository.ProfessionRepository;
import com.howl.uwtracker.repository.RunObjectiveRepository;
import com.howl.uwtracker.repository.RunParticipantItemDropRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import com.howl.uwtracker.repository.TrackedItemRepository;
import com.howl.uwtracker.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The actual find-or-create-run + attach-participants work, run inside a single transaction under
 * {@link MapDedupLock}. Kept as a separate bean from {@link UploadRunService} (rather than a private
 * method there) — a {@code @Transactional} method only goes through Spring's proxy, and therefore
 * only actually starts a transaction, when called from a *different* bean; self-invocation within
 * the same class silently skips the proxy entirely.
 */
@Component
public class UploadRunWriter {

    private static final Logger log = LoggerFactory.getLogger(UploadRunWriter.class);
    // Wide enough to absorb realistic clock skew between different party members' own machines
    // (utc_start is stamped client-side, time(nullptr) — not server receive time) without risking
    // merging two genuinely different attempts, which are minutes apart in practice for this map.
    // A wide window alone would also risk merging two unrelated parties that happen to start close
    // together, since map_id is a global zone id, not tied to any specific server/instance — the
    // exact-roster check in findDedupMatch guards against that independently of window size.
    private static final int DEDUP_WINDOW_SECONDS = 60;

    // Duplicated from RoleDerivation's private profession-id constants (also duplicated in
    // RoleDerivationTest and UploadRunIntegrationTest already) — no shared constants class exists
    // in this codebase yet, and introducing one is out of scope here.
    private static final int RANGER_PROFESSION_ID = 2;
    private static final int ASSASSIN_PROFESSION_ID = 7;
    private static final Set<String> TRAPPER_LABELS = Set.of("T1", "T2", "T3");

    private final GameMapRepository gameMapRepository;
    private final RunRepository runRepository;
    private final RunObjectiveRepository runObjectiveRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final PlayerCharacterRepository playerCharacterRepository;
    private final ProfessionRepository professionRepository;
    private final RunParticipantItemDropRepository runParticipantItemDropRepository;
    private final TrackedItemRepository trackedItemRepository;
    private final PersonRepository personRepository;

    public UploadRunWriter(GameMapRepository gameMapRepository,
                            RunRepository runRepository,
                            RunObjectiveRepository runObjectiveRepository,
                            RunParticipantRepository runParticipantRepository,
                            PlayerCharacterRepository playerCharacterRepository,
                            ProfessionRepository professionRepository,
                            RunParticipantItemDropRepository runParticipantItemDropRepository,
                            TrackedItemRepository trackedItemRepository,
                            PersonRepository personRepository) {
        this.gameMapRepository = gameMapRepository;
        this.runRepository = runRepository;
        this.runObjectiveRepository = runObjectiveRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.playerCharacterRepository = playerCharacterRepository;
        this.professionRepository = professionRepository;
        this.runParticipantItemDropRepository = runParticipantItemDropRepository;
        this.trackedItemRepository = trackedItemRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public UploadRunResponse ingest(PartyDto party, List<PartyMemberDto> members, List<String> roles,
                                     ObjectiveSectionDto objective, Long uploaderPersonId, RoleModel roleModel) {
        // Already validated to exist (UploadRunService — (map, party_size) is a curated,
        // migration-seeded set in map_configs).
        GameMap map = gameMapRepository.getReferenceById(party.mapId());
        // A reference proxy, not a fetch — same pattern as `map` above. Resolved here (inside this
        // method's own transaction) rather than passed in as an entity, since the MachineKey/Person
        // lookup in UploadRunService happens outside this transaction (open-in-view is disabled) and
        // a detached lazy-loaded entity from there isn't safe to use in a fresh persistence context.
        Person uploader = personRepository.getReferenceById(uploaderPersonId);

        // utc_start is time(nullptr) — real wall-clock epoch SECONDS — confirmed against a real
        // GWToolboxdll payload sample. (An earlier draft of this assumed epoch milliseconds.)
        Instant targetUtcStart = Instant.ofEpochSecond(party.utcStart());
        Optional<Run> existing = findDedupMatch(party.mapId(), targetUtcStart, members);

        Run run;
        boolean created;
        if (existing.isPresent()) {
            run = existing.get();
            created = false;
        } else {
            run = createRun(map, targetUtcStart, party, objective, members.size());
            created = true;
        }

        attachParticipants(run, members, roles, uploader);
        // Cross-upload T1/T2/T3-by-elimination only applies to the Underworld trapper model; other
        // role models resolve every member deterministically from one upload, with nothing to infer.
        if (roleModel == RoleModel.TRAPPER) {
            inferRemainingTrapperRoleByElimination(run);
        }

        return new UploadRunResponse(run.getId(), created);
    }

    /**
     * Among the runs on this map within the dedup time window, picks the one whose existing
     * participant roster exactly matches this upload's party — not just the closest in time. A wide
     * time window alone can catch more than one candidate (e.g. two unrelated parties starting close
     * together on the same globally-shared map_id); requiring an exact roster match is what actually
     * guards against merging them, independently of how wide the window is.
     */
    private Optional<Run> findDedupMatch(Integer mapId, Instant targetUtcStart, List<PartyMemberDto> members) {
        Set<String> incomingNames = members.stream().map(PartyMemberDto::name).collect(Collectors.toSet());
        for (Run candidate : runRepository.findDedupCandidates(mapId, targetUtcStart, DEDUP_WINDOW_SECONDS)) {
            Set<String> existingNames = Set.copyOf(runParticipantRepository.findRawNamesByRunId(candidate.getId()));
            if (existingNames.equals(incomingNames)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Run createRun(GameMap map, Instant targetUtcStart, PartyDto party, ObjectiveSectionDto objective, int partySize) {
        List<ObjectiveDto> objectives = objective.objectives() == null ? List.of() : objective.objectives();
        boolean completed = !objectives.isEmpty()
                && Objects.equals(objectives.get(objectives.size() - 1).status(), 2);

        Run run = new Run(
                map,
                targetUtcStart,
                // instance_start is NOT a timestamp — a steady_clock-based ms counter with no
                // absolute meaning (confirmed against a real payload sample) — stored as a raw
                // offset, never converted to an Instant.
                SentinelMapper.map(objective.instanceStart()),
                // objective.utc_start is also real wall-clock epoch seconds, same as party.utc_start.
                epochSecondsToInstant(objective.utcStart()),
                party.endReason(),
                completed,
                SentinelMapper.map(objective.duration()),
                partySize
        );
        run = runRepository.save(run);

        int sequence = 0;
        for (ObjectiveDto obj : objectives) {
            runObjectiveRepository.save(new RunObjective(
                    run,
                    sequence++,
                    obj.name(),
                    obj.status(),
                    SentinelMapper.map(obj.start()),
                    SentinelMapper.map(obj.done()),
                    SentinelMapper.map(obj.duration()),
                    obj.indent() == null ? 0 : obj.indent()
            ));
        }
        return run;
    }

    private void attachParticipants(Run run, List<PartyMemberDto> members, List<String> roles, Person uploader) {
        for (int i = 0; i < members.size(); i++) {
            PartyMemberDto member = members.get(i);
            String role = roles.get(i);

            PlayerCharacter character = playerCharacterRepository.findByCharacterName(member.name()).orElse(null);
            Profession primary = resolveProfession(member.primary(), "primary");
            Profession secondary = member.secondary() == null ? null : resolveProfession(member.secondary(), "secondary");
            // Defensive defaults for an older/different payload omitting these fields — assume a
            // real player unless told otherwise.
            boolean isPlayer = member.isPlayer() == null || member.isPlayer();
            boolean isHero = member.isHero() != null && member.isHero();
            boolean isHenchman = member.isHenchman() != null && member.isHenchman();
            int deaths = member.deaths() == null ? 0 : member.deaths();
            // Unlike deaths, null itself is meaningful here (no gambling this run, or an older
            // plugin build that doesn't report it), so it's passed through rather than defaulted —
            // except a reported net of exactly 0 is collapsed to null too, so a wash isn't stored as
            // distinguishable from "didn't gamble" (Gamblers Anonymous shouldn't count either as a
            // gambled run).
            Integer gamblingStoneNet = member.gamblingStoneNet() == null || member.gamblingStoneNet() == 0
                    ? null : member.gamblingStoneNet();

            Optional<RunParticipant> existing = runParticipantRepository.findByRun_IdAndRawName(run.getId(), member.name());
            RunParticipant participant;
            if (existing.isPresent()) {
                participant = existing.get();
                participant.setCharacter(character);
                participant.setPrimaryProfession(primary);
                participant.setSecondaryProfession(secondary);
                // A null role here means THIS upload had no reliable data for this member (not their
                // own self-report and no profession-combo match) — never erase an earlier upload's
                // known role with that absence. A non-null role always overwrites: for a self-report
                // that's the authoritative update; for a profession-combo role it's the same
                // deterministic value every time anyway (see RoleDerivation).
                if (role != null) {
                    participant.setRole(role);
                }
                participant.setPlayer(isPlayer);
                participant.setHero(isHero);
                participant.setHenchman(isHenchman);
                participant.setDeaths(deaths);
                participant.setGamblingStoneNet(gamblingStoneNet);
                participant.setUploadedByPerson(uploader);
                // party_index intentionally left as originally recorded — not re-derived on resend
            } else {
                participant = runParticipantRepository.save(new RunParticipant(
                        run, character, member.name(), primary, secondary, role, i, isPlayer, isHero, isHenchman,
                        deaths, uploader, gamblingStoneNet));
            }
            attachItemDrops(participant, member.itemDrops());
        }
    }

    /**
     * The server-side counterpart to the plugin's old (now-removed) MaybeAssignT1ByElimination —
     * generalized to whichever of T1/T2/T3 is missing, not just T1, since self-reporting means any
     * of the three could end up being the one nobody's uploaded for yet. Operates on the run's
     * accumulated participant rows (across however many uploads have arrived so far), not just this
     * upload's own data — a single upload can supply at most one self-reported hint now, so this can
     * never resolve from one call to RoleDerivation.resolveRoles alone. Only infers when exactly one
     * Ranger/Assassin-combo participant remains unassigned with the other two roles both known —
     * same "don't guess when ambiguous" guard as the original client-side logic. Idempotent: a no-op
     * once all three are already resolved, or if there isn't a unique remaining candidate.
     */
    private void inferRemainingTrapperRoleByElimination(Run run) {
        List<RunParticipant> trapperCandidates = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId())
                .stream()
                .filter(p -> p.getPrimaryProfession().getId() == RANGER_PROFESSION_ID
                        && p.getSecondaryProfession() != null && p.getSecondaryProfession().getId() == ASSASSIN_PROFESSION_ID)
                .toList();

        Set<String> assigned = trapperCandidates.stream()
                .map(RunParticipant::getRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<RunParticipant> unassigned = trapperCandidates.stream().filter(p -> p.getRole() == null).toList();

        if (assigned.size() == 2 && unassigned.size() == 1) {
            Set<String> missing = new HashSet<>(TRAPPER_LABELS);
            missing.removeAll(assigned);
            RunParticipant last = unassigned.get(0);
            last.setRole(missing.iterator().next());
            runParticipantRepository.save(last);
        }
    }

    /**
     * Replaces this participant's recorded drops wholesale rather than diffing — same "a resend can
     * correct stale data" rationale as the scalar fields above, just applied to a list. Drops for an
     * item id not in {@code tracked_items} (e.g. an older backend that hasn't been migrated for a
     * newly-tracked item yet) are skipped with a WARN rather than failing the whole upload — the
     * item_id -> tracked_items FK would otherwise reject the entire transaction over one bad row.
     */
    private void attachItemDrops(RunParticipant participant, List<ItemDropDto> itemDrops) {
        runParticipantItemDropRepository.deleteById_RunParticipantId(participant.getId());
        if (itemDrops == null) {
            return;
        }
        for (ItemDropDto drop : itemDrops) {
            if (!trackedItemRepository.existsById(drop.id())) {
                log.warn("ignoring drop for untracked item id {} (participant {})", drop.id(), participant.getRawName());
                continue;
            }
            int count = drop.count() == null ? 0 : drop.count();
            runParticipantItemDropRepository.save(new RunParticipantItemDrop(
                    new RunParticipantItemDropId(participant.getId(), drop.id()), count));
        }
    }

    private Profession resolveProfession(Integer professionId, String slot) {
        return professionRepository.findById(professionId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "unknown " + slot + " profession id " + professionId));
    }

    private Instant epochSecondsToInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }
}
