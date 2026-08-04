package com.howl.uwtracker.loserboards;

import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.history.RunSpecifications;
import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.leaderboards.dto.ParticipantSummary;
import com.howl.uwtracker.loserboards.dto.RoleUserDeathsResponse;
import com.howl.uwtracker.loserboards.dto.RoleUserFailResponse;
import com.howl.uwtracker.loserboards.dto.UserResignResponse;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * specs/frontend "Loserboards" — the mirror image of {@code LeaderboardService}, kept as its own
 * package/page rather than folded into the leaderboards one.
 *
 * <p>{@code worst} is {@code @Transactional(readOnly = true)} for the same reason
 * {@code LeaderboardService.overall} is: with {@code spring.jpa.open-in-view=false}, the Hibernate
 * session closes as soon as the repository call returns, and {@link ParticipantSummary#from}
 * touches the lazy {@code character}/{@code person} associations — that mapping has to happen
 * inside the same transaction as the query.
 */
@Service
public class LoserboardService {

    private static final int DEFAULT_LIMIT = 10;

    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final LoserboardQueryRepository loserboardQueryRepository;

    public LoserboardService(RunRepository runRepository, RunParticipantRepository runParticipantRepository,
                              LoserboardQueryRepository loserboardQueryRepository) {
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.loserboardQueryRepository = loserboardQueryRepository;
    }

    /** The slowest completed runs — the mirror of {@code LeaderboardService.overall}, which is fastest-first. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> worst(Integer mapId, Integer limit, Instant from, Instant to) {
        Specification<Run> spec = Specification.where(RunSpecifications.hasMap(mapId))
                .and(RunSpecifications.isCompleted(true))
                .and(RunSpecifications.startedBetween(from, to));
        Sort slowestFirst = Sort.by(Sort.Direction.DESC, "durationMs");
        List<Run> runs = runRepository.findAll(spec, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit, slowestFirst)).getContent();

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

    public List<RoleUserDeathsResponse> roleDeaths(Integer mapId, Instant from, Instant to) {
        return loserboardQueryRepository.findRoleDeaths(mapId, from, to);
    }

    public List<RoleUserFailResponse> roleFails(Integer mapId, Instant from, Instant to) {
        return loserboardQueryRepository.findRoleFails(mapId, from, to);
    }

    public List<UserResignResponse> globalFails(Integer mapId, Instant from, Instant to) {
        return loserboardQueryRepository.findGlobalFails(mapId, from, to);
    }
}
