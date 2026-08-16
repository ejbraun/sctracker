package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.history.RunSpecifications;
import com.howl.uwtracker.leaderboards.dto.ItemDropLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.leaderboards.dto.ParticipantSummary;
import com.howl.uwtracker.leaderboards.dto.PersonalBestEntryResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalSectionBestResponse;
import com.howl.uwtracker.leaderboards.dto.SectionEntryResponse;
import com.howl.uwtracker.leaderboards.dto.UserStreakResponse;
import com.howl.uwtracker.repository.RoleObjectiveRepository;
import com.howl.uwtracker.repository.RunObjectiveRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * specs/backend/05-leaderboards.md.
 *
 * <p>{@code overall}/{@code section} are {@code @Transactional(readOnly = true)} deliberately:
 * with {@code spring.jpa.open-in-view=false} (spec 01), the Hibernate session closes as soon as
 * each repository call returns. {@link ParticipantSummary#from} and the {@code run.getUtcStart()}
 * access below touch lazy associations (character, run), which would throw
 * {@code LazyInitializationException} if that DTO mapping happened after the session closed —
 * hence doing it inside one transaction that spans both the repository calls and the mapping.
 */
@Service
public class LeaderboardService {

    private static final int DEFAULT_LIMIT = 10;

    private final RunRepository runRepository;
    private final RunObjectiveRepository runObjectiveRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RoleObjectiveRepository roleObjectiveRepository;
    private final LeaderboardQueryRepository leaderboardQueryRepository;

    public LeaderboardService(RunRepository runRepository, RunObjectiveRepository runObjectiveRepository,
                               RunParticipantRepository runParticipantRepository,
                               RoleObjectiveRepository roleObjectiveRepository,
                               LeaderboardQueryRepository leaderboardQueryRepository) {
        this.runRepository = runRepository;
        this.runObjectiveRepository = runObjectiveRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.roleObjectiveRepository = roleObjectiveRepository;
        this.leaderboardQueryRepository = leaderboardQueryRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> overall(Integer mapId, Integer limit, Instant from, Instant to) {
        Specification<Run> spec = Specification.where(RunSpecifications.hasMap(mapId))
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
    public List<SectionEntryResponse> section(Integer mapId, String objectiveName, Integer limit, Instant from, Instant to) {
        List<RunObjective> objectives = runObjectiveRepository.findFastestForMapObjective(
                mapId, objectiveName, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit));

        Set<String> gatedRoles = gatedRolesFor(mapId, objectiveName);

        return objectives.stream()
                .map(ro -> {
                    List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ro.getRun().getId())
                            .stream()
                            .filter(rp -> gatedRoles.contains(rp.getRole()))
                            .map(ParticipantSummary::from)
                            .toList();
                    return new SectionEntryResponse(ro.getRun().getId(), ro.getDurationMs(), ro.getRun().getUtcStart(),
                            ro.getStartMs(), ro.getDoneMs(), participants);
                })
                .toList();
    }

    /**
     * "Fastest to finish this objective" — elapsed run time at completion (doneMs), role-gated the
     * same way as {@link #section} (that objective's own clear speed): both are about who earns
     * credit for the objective, just measured from two different reference points.
     */
    @Transactional(readOnly = true)
    public List<SectionEntryResponse> sectionFinish(Integer mapId, String objectiveName, Integer limit, Instant from, Instant to) {
        List<RunObjective> objectives = runObjectiveRepository.findFastestDoneForMapObjective(
                mapId, objectiveName, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit));

        Set<String> gatedRoles = gatedRolesFor(mapId, objectiveName);

        return objectives.stream()
                .map(ro -> {
                    List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ro.getRun().getId())
                            .stream()
                            .filter(rp -> gatedRoles.contains(rp.getRole()))
                            .map(ParticipantSummary::from)
                            .toList();
                    return new SectionEntryResponse(ro.getRun().getId(), ro.getDurationMs(), ro.getRun().getUtcStart(),
                            ro.getStartMs(), ro.getDoneMs(), participants);
                })
                .toList();
    }

    /**
     * "Fastest to reach this objective" — pacing, not clear speed (see {@link #section}). Not
     * role-gated: the full party is shown, since everyone in the run shared that pace, not just
     * whichever role "earns credit" for the objective itself.
     */
    @Transactional(readOnly = true)
    public List<SectionEntryResponse> sectionStart(Integer mapId, String objectiveName, Integer limit, Instant from, Instant to) {
        List<RunObjective> objectives = runObjectiveRepository.findFastestStartForMapObjective(
                mapId, objectiveName, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit));

        return objectives.stream()
                .map(ro -> {
                    List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ro.getRun().getId())
                            .stream()
                            .map(ParticipantSummary::from)
                            .toList();
                    return new SectionEntryResponse(ro.getRun().getId(), ro.getDurationMs(), ro.getRun().getUtcStart(),
                            ro.getStartMs(), ro.getDoneMs(), participants);
                })
                .toList();
    }

    public Long personalOverallBestMs(Long personId, Integer mapId) {
        return leaderboardQueryRepository.findPersonalOverallBestMs(personId, mapId);
    }

    @Transactional(readOnly = true)
    public List<PersonalBestEntryResponse> personalOverallTop(Long personId, Integer mapId, Integer limit, Instant from, Instant to) {
        List<PersonalBestRunRef> refs = leaderboardQueryRepository.findPersonalOverallTop(
                personId, mapId, limit == null ? DEFAULT_LIMIT : limit, from, to);

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

    /**
     * The full gated party of the run that earned the person's best time — not just the person's own
     * character. Filtering by {@code person_id} only picks out *which run* is "yours"; once that run
     * is known, everyone in it who was gated in for this objective shares the credit, same as the
     * "Global" section board (see {@link #section}).
     */
    @Transactional(readOnly = true)
    public PersonalSectionBestResponse personalSectionBestMs(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        PersonalSectionBestRunRef ref = leaderboardQueryRepository.findPersonalSectionBestRun(personId, mapId, objectiveName, from, to);
        if (ref == null) {
            return null;
        }

        Set<String> gatedRoles = gatedRolesFor(mapId, objectiveName);
        List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ref.runId())
                .stream()
                .filter(rp -> gatedRoles.contains(rp.getRole()))
                .map(ParticipantSummary::from)
                .toList();

        return new PersonalSectionBestResponse(ref.durationMs(), ref.startMs(), ref.doneMs(), participants);
    }

    /**
     * The person's own fastest finish of this objective (across every linked character), role-gated
     * the same way as {@link #personalSectionBestMs} — see {@link #sectionFinish}.
     */
    @Transactional(readOnly = true)
    public PersonalSectionBestResponse personalSectionFinishMs(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        PersonalSectionBestRunRef ref = leaderboardQueryRepository.findPersonalSectionFinishRun(personId, mapId, objectiveName, from, to);
        if (ref == null) {
            return null;
        }

        Set<String> gatedRoles = gatedRolesFor(mapId, objectiveName);
        List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ref.runId())
                .stream()
                .filter(rp -> gatedRoles.contains(rp.getRole()))
                .map(ParticipantSummary::from)
                .toList();

        return new PersonalSectionBestResponse(ref.durationMs(), ref.startMs(), ref.doneMs(), participants);
    }

    /**
     * The person's own fastest arrival at this objective (across every linked character), full party
     * of that run shown — see {@link #sectionStart} for why this isn't role-gated like
     * {@link #personalSectionBestMs}.
     */
    @Transactional(readOnly = true)
    public PersonalSectionBestResponse personalSectionFastestStart(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        PersonalSectionBestRunRef ref = leaderboardQueryRepository.findPersonalSectionFastestStartRun(personId, mapId, objectiveName, from, to);
        if (ref == null) {
            return null;
        }

        List<ParticipantSummary> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(ref.runId())
                .stream()
                .map(ParticipantSummary::from)
                .toList();

        return new PersonalSectionBestResponse(ref.durationMs(), ref.startMs(), ref.doneMs(), participants);
    }

    /** Global ranking only — no personal "Yours" counterpart for this stat. */
    public List<UserStreakResponse> longestCompletedStreak(Integer mapId, Integer limit, Instant from, Instant to) {
        return leaderboardQueryRepository.findLongestCompletedStreak(mapId, limit == null ? DEFAULT_LIMIT : limit, from, to);
    }

    /** Global ranking only — no personal "Yours" counterpart for this stat. */
    public List<ItemDropLeaderResponse> luckiestPlayers(Integer mapId, Instant from, Instant to) {
        return leaderboardQueryRepository.findLuckiestPlayers(mapId, from, to);
    }

    private Set<String> gatedRolesFor(Integer mapId, String objectiveName) {
        return roleObjectiveRepository.findById_MapIdAndId_ObjectiveName(mapId, objectiveName).stream()
                .map(ro -> ro.getId().getRole())
                .collect(Collectors.toSet());
    }
}
