package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Run;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long>, JpaSpecificationExecutor<Run> {

    /**
     * Dedup lookup: existing run for this map within +/- windowSeconds of targetUtcStart,
     * closest match first. Native query — TIMESTAMPDIFF's unit keyword doesn't survive JPQL's
     * portable function() escape.
     */
    @Query(value = "SELECT * FROM runs " +
            "WHERE map_id = :mapId " +
            "AND utc_start BETWEEN :targetUtcStart - INTERVAL :windowSeconds SECOND " +
            "AND :targetUtcStart + INTERVAL :windowSeconds SECOND " +
            "ORDER BY ABS(TIMESTAMPDIFF(MICROSECOND, utc_start, :targetUtcStart)) ASC " +
            "LIMIT 1", nativeQuery = true)
    Optional<Run> findDedupMatch(@Param("mapId") Integer mapId,
                                  @Param("targetUtcStart") Instant targetUtcStart,
                                  @Param("windowSeconds") int windowSeconds);
}
