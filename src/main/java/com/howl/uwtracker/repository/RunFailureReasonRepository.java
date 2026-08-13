package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RunFailureReason;
import com.howl.uwtracker.domain.RunFailureReasonId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunFailureReasonRepository extends JpaRepository<RunFailureReason, RunFailureReasonId> {

    /** Clears a run's previously recorded failure reasons before re-inserting on a resubmit — see FailureReportService. */
    void deleteById_RunId(Long runId);
}
