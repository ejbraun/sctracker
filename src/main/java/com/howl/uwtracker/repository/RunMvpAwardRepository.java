package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RunMvpAward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunMvpAwardRepository extends JpaRepository<RunMvpAward, Long> {

    /** Clears a run's previously recorded MVP award before re-inserting on a resubmit — see MvpReportPersister. */
    void deleteByRun_Id(Long runId);
}
