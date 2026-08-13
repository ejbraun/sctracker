package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Run;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RunRepository extends JpaRepository<Run, Long>, JpaSpecificationExecutor<Run> {

    /**
     * Dedup candidates: every run for this map within +/- windowSeconds of targetUtcStart, closest
     * match first — a wide window (absorbing realistic clock skew between different party members'
     * own machines, which is what utc_start is stamped from) can genuinely catch more than one
     * candidate, e.g. two unrelated parties running the same map close together in time. The caller
     * (UploadRunWriter) picks among these by roster match rather than time alone. Capped at 5 — "a
     * handful of concurrent uploads at most" per spec 02, so more than that within the window would
     * be unexpected. Native query — TIMESTAMPDIFF's unit keyword doesn't survive JPQL's portable
     * function() escape.
     */
    @Query(value = "SELECT * FROM runs " +
            "WHERE map_id = :mapId " +
            "AND utc_start BETWEEN :targetUtcStart - INTERVAL :windowSeconds SECOND " +
            "AND :targetUtcStart + INTERVAL :windowSeconds SECOND " +
            "ORDER BY ABS(TIMESTAMPDIFF(MICROSECOND, utc_start, :targetUtcStart)) ASC " +
            "LIMIT 5", nativeQuery = true)
    List<Run> findDedupCandidates(@Param("mapId") Integer mapId,
                                   @Param("targetUtcStart") Instant targetUtcStart,
                                   @Param("windowSeconds") int windowSeconds);
}
