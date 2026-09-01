package com.howl.uwtracker.loserboards;

import com.howl.uwtracker.domain.MapConfig;
import com.howl.uwtracker.domain.MapConfigId;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.history.RunSpecifications;
import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.leaderboards.dto.ParticipantSummary;
import com.howl.uwtracker.leaderboards.dto.SectionEntryResponse;
import com.howl.uwtracker.leaderboards.dto.UserStreakResponse;
import com.howl.uwtracker.loserboards.dto.OutdatedPluginResponse;
import com.howl.uwtracker.loserboards.dto.RoleFailureReasonResponse;
import com.howl.uwtracker.loserboards.dto.RoleUserDeathsResponse;
import com.howl.uwtracker.loserboards.dto.UserResignResponse;
import com.howl.uwtracker.plugin.PluginVersionMetadata;
import com.howl.uwtracker.plugin.PluginVersionMetadataLoader;
import com.howl.uwtracker.repository.MapConfigRepository;
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
 * specs/frontend "Loserboards" — the mirror of {@code LeaderboardService}, and it takes the same
 * optional {@code partySize} dimension ({@code null} = all sizes for the map). See
 * specs/features/fow-and-party-size.md.
 */
@Service
public class LoserboardService {

    private static final int DEFAULT_LIMIT = 10;

    private final RunRepository runRepository;
    private final RunObjectiveRepository runObjectiveRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RoleObjectiveRepository roleObjectiveRepository;
    private final MapConfigRepository mapConfigRepository;
    private final LoserboardQueryRepository loserboardQueryRepository;
    private final PluginVersionMetadataLoader pluginVersionMetadataLoader;

    public LoserboardService(RunRepository runRepository, RunObjectiveRepository runObjectiveRepository,
                              RunParticipantRepository runParticipantRepository,
                              RoleObjectiveRepository roleObjectiveRepository,
                              MapConfigRepository mapConfigRepository,
                              LoserboardQueryRepository loserboardQueryRepository,
                              PluginVersionMetadataLoader pluginVersionMetadataLoader) {
        this.runRepository = runRepository;
        this.runObjectiveRepository = runObjectiveRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.roleObjectiveRepository = roleObjectiveRepository;
        this.mapConfigRepository = mapConfigRepository;
        this.loserboardQueryRepository = loserboardQueryRepository;
        this.pluginVersionMetadataLoader = pluginVersionMetadataLoader;
    }

    /** The slowest completed runs — the mirror of {@code LeaderboardService.overall}. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> worst(Integer mapId, Integer partySize, Integer limit, Instant from, Instant to) {
        Specification<Run> spec = Specification.where(RunSpecifications.hasMap(mapId))
                .and(RunSpecifications.hasPartySize(partySize))
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

    public List<RoleUserDeathsResponse> roleDeaths(Integer mapId, Integer partySize, Instant from, Instant to) {
        return loserboardQueryRepository.findRoleDeaths(mapId, partySize, from, to);
    }

    public List<UserResignResponse> globalFails(Integer mapId, Integer partySize, Instant from, Instant to) {
        return loserboardQueryRepository.findGlobalFails(mapId, partySize, from, to);
    }

    public List<RoleFailureReasonResponse> roleFailureReasons(Integer mapId, Integer partySize, Instant from, Instant to) {
        return loserboardQueryRepository.findRoleFailureReasons(mapId, partySize, from, to);
    }

    /**
     * Active users whose plugin is behind the current minimum version — not map-scoped, see
     * {@link LoserboardQueryRepository#findOutdatedActivePlugins}. Empty when the plugin manifest
     * hasn't loaded (no {@code PLUGIN_STORAGE_BUCKET}, or unreachable): with no known current
     * version there's nothing to compare against, so nobody can be classified as outdated — the
     * same "fails open" posture as {@code PluginVersionMetadataLoader.requireCurrentVersion}.
     */
    public List<OutdatedPluginResponse> outdatedPlugins(Instant from, Instant to) {
        PluginVersionMetadata current = pluginVersionMetadataLoader.getCurrent();
        if (current == null) {
            return List.of();
        }
        return loserboardQueryRepository.findOutdatedActivePlugins(from, to, current.version());
    }

    /** Global ranking only — no personal "Yours" counterpart for this stat. */
    public List<UserStreakResponse> longestBadStreak(Integer mapId, Integer partySize, Integer limit, Instant from, Instant to) {
        return loserboardQueryRepository.findLongestBadStreak(mapId, partySize, limit == null ? DEFAULT_LIMIT : limit, from, to);
    }

    /**
     * Slowest to reach this objective — the mirror of {@code LeaderboardService.sectionStart}.
     * Gated by whichever roles are involved in that objective when the config has a role model;
     * for a role-less config (FoW 8-man) the whole party is shown.
     */
    @Transactional(readOnly = true)
    public List<SectionEntryResponse> sectionSlowestStart(Integer mapId, Integer partySize, String objectiveName, Integer limit, Instant from, Instant to) {
        List<RunObjective> objectives = runObjectiveRepository.findSlowestStartForMapObjective(
                mapId, objectiveName, partySize, from, to, PageRequest.of(0, limit == null ? DEFAULT_LIMIT : limit));

        boolean gated = isRoleGated(mapId, partySize);
        Set<String> gatedRoles = gated
                ? roleObjectiveRepository.findById_MapIdAndId_ObjectiveName(mapId, objectiveName).stream()
                        .map(ro -> ro.getId().getRole())
                        .collect(Collectors.toSet())
                : Set.of();

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

    /** Same rule as {@code LeaderboardService.isRoleGated}. */
    private boolean isRoleGated(Integer mapId, Integer partySize) {
        if (partySize != null) {
            return mapConfigRepository.findById(new MapConfigId(mapId, partySize))
                    .map(c -> c.getRoleModel() != null)
                    .orElse(false);
        }
        List<MapConfig> configs = mapConfigRepository.findByIdMapIdOrderByIdPartySizeAsc(mapId);
        return configs.size() == 1 && configs.get(0).getRoleModel() != null;
    }
}
