package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RunFailureReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunFailureReasonRepository extends JpaRepository<RunFailureReason, Long> {

    /** Clears a run's previously recorded failure reasons before re-inserting on a resubmit — see FailureReportService. */
    void deleteByRun_Id(Long runId);

    /** Backs the RunDetail "Failure Reasons" panel. */
    List<RunFailureReason> findByRun_Id(Long runId);
}
