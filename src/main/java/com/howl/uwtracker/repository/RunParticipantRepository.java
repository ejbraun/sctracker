package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.RunParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RunParticipantRepository extends JpaRepository<RunParticipant, Long> {

    List<RunParticipant> findByRun_IdOrderByPartyIndexAsc(Long runId);

    Optional<RunParticipant> findByRun_IdAndRawName(Long runId, String rawName);

    long countByRun_Id(Long runId);

    @Query("select rp.rawName from RunParticipant rp where rp.run.id = :runId")
    List<String> findRawNamesByRunId(@Param("runId") Long runId);

    /** Validates a failure-report submission against the run's actual roster — see FailureReportService. */
    @Query("select distinct rp.role from RunParticipant rp where rp.run.id = :runId and rp.role is not null")
    Set<String> findDistinctRolesByRunId(@Param("runId") Long runId);

    @Modifying
    @Query("update RunParticipant rp set rp.character = :character " +
            "where rp.character is null and rp.rawName = :rawName")
    int backfillCharacter(@Param("character") PlayerCharacter character, @Param("rawName") String rawName);
}
