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

    /**
     * {@code from}/{@code to} are optional (null means unbounded) — backs the leaderboard time-window
     * filter. {@code status = 2} (Completed) excludes Failed objectives — GWToolboxdll still fills in
     * a real {@code durationMs} for those (elapsed time up to the retroactive end-of-run fail marker),
     * so without this filter a quick death can out-rank a genuine clear.
     */
    @Query("select ro from RunObjective ro where ro.run.map.id = :mapId and ro.name = :name " +
            "and ro.status = 2 and ro.durationMs is not null " +
            "and (:from is null or ro.run.utcStart >= :from) and (:to is null or ro.run.utcStart <= :to) " +
            "order by ro.durationMs asc")
    List<RunObjective> findFastestForMapObjective(@Param("mapId") Integer mapId, @Param("name") String name,
                                                   @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * "Fastest to reach this objective" — a different question from {@link #findFastestForMapObjective}:
     * pacing up to the objective, not the objective's own clear speed. {@code startMs} is set once an
     * objective transitions to Started regardless of whether it later completes or fails, so unlike
     * the fastest-clear query above there's no {@code status = 2} filter here.
     */
    @Query("select ro from RunObjective ro where ro.run.map.id = :mapId and ro.name = :name " +
            "and ro.startMs is not null " +
            "and (:from is null or ro.run.utcStart >= :from) and (:to is null or ro.run.utcStart <= :to) " +
            "order by ro.startMs asc")
    List<RunObjective> findFastestStartForMapObjective(@Param("mapId") Integer mapId, @Param("name") String name,
                                                         @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * "Fastest to finish this objective" — elapsed run time when the objective completed
     * ({@code doneMs}), a third question distinct from {@link #findFastestForMapObjective} (that
     * objective's own isolated clear speed) and {@link #findFastestStartForMapObjective} (pacing up
     * to its start). Same {@code status = 2} filter as the clear-speed query and for the same
     * reason: GWToolboxdll still fills in a real {@code doneMs} for a Failed objective (the
     * retroactive end-of-run fail marker), so without this filter a quick wipe could out-rank a
     * genuine finish.
     */
    @Query("select ro from RunObjective ro where ro.run.map.id = :mapId and ro.name = :name " +
            "and ro.status = 2 and ro.doneMs is not null " +
            "and (:from is null or ro.run.utcStart >= :from) and (:to is null or ro.run.utcStart <= :to) " +
            "order by ro.doneMs asc")
    List<RunObjective> findFastestDoneForMapObjective(@Param("mapId") Integer mapId, @Param("name") String name,
                                                        @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /** The mirror of {@link #findFastestStartForMapObjective} — slowest to reach the objective, for Loserboards. */
    @Query("select ro from RunObjective ro where ro.run.map.id = :mapId and ro.name = :name " +
            "and ro.startMs is not null " +
            "and (:from is null or ro.run.utcStart >= :from) and (:to is null or ro.run.utcStart <= :to) " +
            "order by ro.startMs desc")
    List<RunObjective> findSlowestStartForMapObjective(@Param("mapId") Integer mapId, @Param("name") String name,
                                                         @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
