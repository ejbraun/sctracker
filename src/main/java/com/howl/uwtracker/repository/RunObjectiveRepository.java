package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RunObjective;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RunObjectiveRepository extends JpaRepository<RunObjective, Long> {

    List<RunObjective> findByRun_IdOrderBySequenceAsc(Long runId);

    Optional<RunObjective> findTopByRun_IdOrderBySequenceDesc(Long runId);

    /** {@code from}/{@code to} are optional (null means unbounded) — backs the leaderboard time-window filter. */
    @Query("select ro from RunObjective ro where ro.run.map.id = :mapId and ro.name = :name " +
            "and ro.durationMs is not null " +
            "and (:from is null or ro.run.utcStart >= :from) and (:to is null or ro.run.utcStart <= :to) " +
            "order by ro.durationMs asc")
    List<RunObjective> findFastestForMapObjective(@Param("mapId") Integer mapId, @Param("name") String name,
                                                   @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
