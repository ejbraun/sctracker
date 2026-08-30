package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.domain.MapConfig;
import com.howl.uwtracker.domain.MapConfigId;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.history.RunSpecifications;
import com.howl.uwtracker.leaderboards.dto.GamblingStoneLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.ItemDropLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.leaderboards.dto.ParticipantSummary;
import com.howl.uwtracker.leaderboards.dto.PersonalBestEntryResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalSectionBestResponse;
import com.howl.uwtracker.leaderboards.dto.RoleMvpAwardResponse;
import com.howl.uwtracker.leaderboards.dto.SectionEntryResponse;
import com.howl.uwtracker.leaderboards.dto.UserStreakResponse;
import com.howl.uwtracker.repository.MapConfigRepository;
import com.howl.uwtracker.repository.RoleObjectiveRepository;
import com.howl.uwtracker.repository.RunObjectiveRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * specs/backend/05-leaderboards.md, plus the party-size dimension from
 * specs/features/fow-and-party-size.md: every map-scoped board takes an optional {@code partySize}
 * ({@code null} = all sizes for the map, non-null = that size only), since a map can now have more
 * than one supported party size (The Fissure of Woe: 2 and 8) with different run mechanics.
 *
 * <p>Role-gating of the section boards depends on the {@code (map, party_size)} config's role model
 * ({@link #isRoleGated}): the Underworld trapper team and the FoW duo are role-gated via
 * {@code role_objectives}; FoW 8-man has no role model, so its section times/participants aren't
 * gated at all.
 *
 * <p>{@code overall}/{@code section} are {@code @Transactional(readOnly = true)} deliberately —
 * see the class's original javadoc reasoning re: {@code open-in-view=false} and lazy associations.
 */
@Service
public class LeaderboardService {

    private static final int DEFAULT_LIMIT = 10;

    private final RunRepository runRepository;
    private final RunObjectiveRepository runObjectiveRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RoleObjectiveRepository roleObjectiveRepository;
    private final MapConfigRepository mapConfigRepository;
    private final LeaderboardQueryRepository leaderboardQueryRepository;

    public LeaderboardService(RunRepository runRepository, RunObjectiveRepository runObjectiveRepository,
                               RunParticipantRepository runParticipantRepository,
                               RoleObjectiveRepository roleObjectiveRepository,
                               MapConfigRepository mapConfigRepository,
                               LeaderboardQueryRepository leaderboardQueryRepository) {
        this.runRepository = runRepository;
        this.runObjectiveRepository = runObjectiveRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.roleObjectiveRepository = roleObjectiveRepository;
        this.mapConfigRepository = mapConfigRepository;
        this.leaderboardQueryRepository = leaderboardQueryRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> overall(Integer mapId, Integer partySize, Integer limit, Instant from, Instant to) {
        Specification<Run> spec = Specification.where(RunSpecifications.hasMap(mapId))
                .and(RunSpecifications.hasPartySize(partySize))
                .and(RunSpecifications.isCompleted(true))
                .and(RunSpecifications.startedBetween(from, to));
        Sort fastestFirst = Sort.by(Sort.Direction.ASC, "durationMs");
        List<Run> runs = runRepository.findAll(spec, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit, fastestFirst)).getContent();

        return runs.stream()
                .map(run -> {
                    List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId())
                            .stream()
                            .map(ParticipantSummary::from)
                            .toList();
                    return new LeaderboardEntryResponse(run.getId(), run.getDurationMs(), run.getUtcStart(), participants);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SectionEntryResponse> section(Integer mapId, Integer partySize, String objectiveName, Integer limit, Instant from, Instant to) {
        return sectionEntries(runObjectiveRepository.findFastestForMapObjective(
                mapId, objectiveName, partySize, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit)),
                mapId, partySize, objectiveName);
    }

    /**
     * "Fastest to finish this objective" — elapsed run time at completion (doneMs), gated the same
     * way as {@link #section}.
     */
    @Transactional(readOnly = true)
    public List<SectionEntryResponse> sectionFinish(Integer mapId, Integer partySize, String objectiveName, Integer limit, Instant from, Instant to) {
        return sectionEntries(runObjectiveRepository.findFastestDoneForMapObjective(
                mapId, objectiveName, partySize, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit)),
                mapId, partySize, objectiveName);
    }

    /**
     * "Fastest to reach this objective" — pacing, not clear speed. Gated the same way as
     * {@link #section}.
     */
    @Transactional(readOnly = true)
    public List<SectionEntryResponse> sectionStart(Integer mapId, Integer partySize, String objectiveName, Integer limit, Instant from, Instant to) {
        return sectionEntries(runObjectiveRepository.findFastestStartForMapObjective(
                mapId, objectiveName, partySize, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit)),
                mapId, partySize, objectiveName);
    }

    private List<SectionEntryResponse> sectionEntries(List<RunObjective> objectives, Integer mapId, Integer partySize, String objectiveName) {
        boolean gated = isRoleGated(mapId, partySize);
        Set<String> gatedRoles = gated ? gatedRolesFor(mapId, objectiveName) : Set.of();
        return objectives.stream()
                .map(ro -> {
                    List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ro.getRun().getId())
                            .stream()
                            .filter(rp -> !gated || gatedRoles.contains(rp.getRole()))
                            .map(ParticipantSummary::from)
                            .toList();
                    return new SectionEntryResponse(ro.getRun().getId(), ro.getDurationMs(), ro.getRun().getUtcStart(),
                            ro.getStartMs(), ro.getDoneMs(), participants);
                })
                .toList();
    }

    public Long personalOverallBestMs(Long personId, Integer mapId, Integer partySize) {
        return leaderboardQueryRepository.findPersonalOverallBestMs(personId, mapId, partySize);
    }

    @Transactional(readOnly = true)
    public List<PersonalBestEntryResponse> personalOverallTop(Long personId, Integer mapId, Integer partySize, Integer limit, Instant from, Instant to) {
        List<PersonalBestRunRef> refs = leaderboardQueryRepository.findPersonalOverallTop(
                personId, mapId, partySize, limit == null ? DEFAULT_LIMIT : limit, from, to);

        return refs.stream()
                .map(ref -> {
                    List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ref.runId())
                            .stream()
                            .map(ParticipantSummary::from)
                            .toList();
                    return new PersonalBestEntryResponse(ref.runId(), ref.durationMs(), ref.utcStart(), participants);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonalSectionBestResponse personalSectionBestMs(Long personId, Integer mapId, Integer partySize, String objectiveName, Instant from, Instant to) {
        return personalSection(personId, mapId, partySize, objectiveName, from, to, "duration");
    }

    @Transactional(readOnly = true)
    public PersonalSectionBestResponse personalSectionFinishMs(Long personId, Integer mapId, Integer partySize, String objectiveName, Instant from, Instant to) {
        return personalSection(personId, mapId, partySize, objectiveName, from, to, "finish");
    }

    @Transactional(readOnly = true)
    public PersonalSectionBestResponse personalSectionFastestStart(Long personId, Integer mapId, Integer partySize, String objectiveName, Instant from, Instant to) {
        return personalSection(personId, mapId, partySize, objectiveName, from, to, "start");
    }

    /**
     * The full gated party of the run that earned the person's best time for this objective — not
     * just the person's own character (same "once the run is known, everyone gated in shares the
     * credit" rule as the global section board). For a role-less config the "gated" set is the
     * whole party.
     */
    private PersonalSectionBestResponse personalSection(Long personId, Integer mapId, Integer partySize,
                                                        String objectiveName, Instant from, Instant to, String metric) {
        boolean gated = isRoleGated(mapId, partySize);
        PersonalSectionBestRunRef ref;
        if (gated) {
            ref = switch (metric) {
                case "start" -> leaderboardQueryRepository.findPersonalSectionFastestStartRun(personId, mapId, partySize, objectiveName, from, to);
                case "finish" -> leaderboardQueryRepository.findPersonalSectionFinishRun(personId, mapId, partySize, objectiveName, from, to);
                default -> leaderboardQueryRepository.findPersonalSectionBestRun(personId, mapId, partySize, objectiveName, from, to);
            };
        } else {
            ref = leaderboardQueryRepository.findPersonalSectionBestRunUngated(
                    personId, mapId, requireConcretePartySize(mapId, partySize), objectiveName, from, to, metric);
        }
        if (ref == null) {
            return null;
        }
        Set<String> gatedRoles = gated ? gatedRolesFor(mapId, objectiveName) : Set.of();
        List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ref.runId())
                .stream()
                .filter(rp -> !gated || gatedRoles.contains(rp.getRole()))
                .map(ParticipantSummary::from)
                .toList();
        return new PersonalSectionBestResponse(ref.runId(), ref.durationMs(), ref.startMs(), ref.doneMs(), participants);
    }

    /** Global ranking only — no personal "Yours" counterpart for this stat. */
    public List<UserStreakResponse> longestCompletedStreak(Integer mapId, Integer partySize, Integer limit, Instant from, Instant to) {
        return leaderboardQueryRepository.findLongestCompletedStreak(mapId, partySize, limit == null ? DEFAULT_LIMIT : limit, from, to);
    }

    /** Global ranking only — no personal "Yours" counterpart for this stat. */
    public List<ItemDropLeaderResponse> luckiestPlayers(Integer mapId, Integer partySize, Instant from, Instant to) {
        return leaderboardQueryRepository.findLuckiestPlayers(mapId, partySize, from, to);
    }

    /** Global ranking only. Naturally empty for a role-less config (every participant's role is null). */
    public List<RoleMvpAwardResponse> roleMvpAwards(Integer mapId, Integer partySize, Instant from, Instant to) {
        return leaderboardQueryRepository.findRoleMvpAwards(mapId, partySize, from, to);
    }

    /** Global ranking only. */
    public List<GamblingStoneLeaderResponse> gamblersAnonymous(Integer mapId, Integer partySize, Instant from, Instant to) {
        return leaderboardQueryRepository.findGamblersAnonymous(mapId, partySize, from, to);
    }

    private Set<String> gatedRolesFor(Integer mapId, String objectiveName) {
        return roleObjectiveRepository.findById_MapIdAndId_ObjectiveName(mapId, objectiveName).stream()
                .map(ro -> ro.getId().getRole())
                .collect(Collectors.toSet());
    }

    /**
     * Whether section boards for this {@code (map, party_size)} are role-gated — i.e. the config has
     * a role model. When {@code partySize} is null and the map has exactly one config, that config's
     * model decides; if the map has several configs, an un-sized query can't tell which applies, so
     * it degrades to "not gated" (show the whole party) rather than erroring on a GET.
     */
    boolean isRoleGated(Integer mapId, Integer partySize) {
        if (partySize != null) {
            return mapConfigRepository.findById(new MapConfigId(mapId, partySize))
                    .map(c -> c.getRoleModel() != null)
                    .orElse(false);
        }
        List<MapConfig> configs = mapConfigRepository.findByIdMapIdOrderByIdPartySizeAsc(mapId);
        return configs.size() == 1 && configs.get(0).getRoleModel() != null;
    }

    /**
     * Resolves a concrete party size for the un-gated personal-section query, which filters on
     * {@code runs.party_size} directly. {@code partySize} given → use it; otherwise the map's sole
     * config's size; a map with more than one config queried without {@code partySize} is a
     * 400 (the frontend always sends it for a multi-config map).
     */
    private Integer requireConcretePartySize(Integer mapId, Integer partySize) {
        if (partySize != null) {
            return partySize;
        }
        List<MapConfig> configs = mapConfigRepository.findByIdMapIdOrderByIdPartySizeAsc(mapId);
        if (configs.size() == 1) {
            return configs.get(0).getPartySize();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "partySize is required for map " + mapId);
    }
}
