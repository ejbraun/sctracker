package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.RunParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RunParticipantRepository extends JpaRepository<RunParticipant, Long> {

    List<RunParticipant> findByRun_IdOrderByPartyIndexAsc(Long runId);

    Optional<RunParticipant> findByRun_IdAndRawName(Long runId, String rawName);

    long countByRun_Id(Long runId);

    @Modifying
    @Query("update RunParticipant rp set rp.character = :character " +
            "where rp.character is null and rp.rawName = :rawName")
    int backfillCharacter(@Param("character") PlayerCharacter character, @Param("rawName") String rawName);
}
