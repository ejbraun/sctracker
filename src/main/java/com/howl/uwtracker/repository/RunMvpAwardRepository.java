package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RunMvpAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunMvpAwardRepository extends JpaRepository<RunMvpAward, Long> {

    /** Clears a run's previously recorded MVP award before re-inserting on a resubmit — see MvpReportPersister. */
    void deleteByRun_Id(Long runId);

    /** At most one row per run (UNIQUE(run_id) — see the changelog). Backs the RunDetail "MVP" panel. */
    Optional<RunMvpAward> findByRun_Id(Long runId);
}
