package com.howl.uwtracker.history;

import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.history.dto.ObjectiveEntry;
import com.howl.uwtracker.history.dto.ParticipantEntry;
import com.howl.uwtracker.history.dto.RunDetailResponse;
import com.howl.uwtracker.history.dto.RunFailureReasonEntry;
import com.howl.uwtracker.history.dto.RunMvpAwardEntry;
import com.howl.uwtracker.history.dto.RunSummaryResponse;
import com.howl.uwtracker.repository.RunFailureReasonRepository;
import com.howl.uwtracker.repository.RunMvpAwardRepository;
import com.howl.uwtracker.repository.RunObjectiveRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * specs/backend/06-run-history.md.
 *
 * <p>Both methods are {@code @Transactional(readOnly = true)} for the same reason as
 * {@code LeaderboardService} (see that class's javadoc): with open-in-view disabled, DTO mapping
 * that touches lazy associations (map, character, professions) has to happen before the session
 * closes, i.e. inside this transaction, not back in the controller.
 */
@Service
public class RunHistoryService {

    private final RunRepository runRepository;
    private final RunObjectiveRepository runObjectiveRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RunFailureReasonRepository runFailureReasonRepository;
    private final RunMvpAwardRepository runMvpAwardRepository;

    public RunHistoryService(RunRepository runRepository, RunObjectiveRepository runObjectiveRepository,
                              RunParticipantRepository runParticipantRepository,
                              RunFailureReasonRepository runFailureReasonRepository,
                              RunMvpAwardRepository runMvpAwardRepository) {
        this.runRepository = runRepository;
        this.runObjectiveRepository = runObjectiveRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.runFailureReasonRepository = runFailureReasonRepository;
        this.runMvpAwardRepository = runMvpAwardRepository;
    }

    @Transactional(readOnly = true)
    public Page<RunSummaryResponse> search(RunHistoryFilter filter, Pageable pageable) {
        Specification<Run> spec = Specification.where(RunSpecifications.hasMap(filter.mapId()))
                .and(RunSpecifications.hasPartySize(filter.partySize()))
                .and(RunSpecifications.isCompleted(filter.completed()))
                .and(RunSpecifications.hasEndReason(filter.endReason()))
                .and(RunSpecifications.startedBetween(filter.from(), filter.to()))
                .and(RunSpecifications.hasParticipantMatching(filter.personId(), filter.characterId(), filter.role()));

        return runRepository.findAll(spec, pageable)
                .map(run -> RunSummaryResponse.from(run, (int) runParticipantRepository.countByRun_Id(run.getId())));
    }

    @Transactional(readOnly = true)
    public RunDetailResponse detail(Long runId) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run not found"));

        var objectives = runObjectiveRepository.findByRun_IdOrderBySequenceAsc(runId).stream()
                .map(ObjectiveEntry::from)
                .toList();
        var participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(runId).stream()
                .map(ParticipantEntry::from)
                .toList();
        var failureReasons = runFailureReasonRepository.findByRun_Id(runId).stream()
                .map(RunFailureReasonEntry::from)
                .toList();
        var mvpAward = runMvpAwardRepository.findByRun_Id(runId).map(RunMvpAwardEntry::from).orElse(null);

        return RunDetailResponse.from(run, objectives, participants, failureReasons, mvpAward);
    }
}
