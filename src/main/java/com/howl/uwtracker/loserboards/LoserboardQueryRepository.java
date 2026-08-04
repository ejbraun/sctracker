package com.howl.uwtracker.loserboards;

import com.howl.uwtracker.loserboards.dto.RoleUserDeathsResponse;
import com.howl.uwtracker.loserboards.dto.RoleUserFailResponse;
import com.howl.uwtracker.loserboards.dto.UserResignResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Raw SQL for "Loserboards" aggregates — same rationale as
 * {@code com.howl.uwtracker.leaderboards.LeaderboardQueryRepository}: cross-entity aggregates that
 * don't map cleanly onto a single Spring Data repository.
 */
@Repository
public class LoserboardQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoserboardQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * One row per (role, user) that has ever played that role on this map. {@code user} is
     * {@code COALESCE(alias, raw_name)} — an unlinked participant is grouped by raw name, same
     * fallback the frontend already uses for display. Excludes participants with a null role
     * (unresolved profession combo) since they don't belong to any of the 8 role sections.
     */
    public List<RoleUserFailResponse> findRoleFails(Integer mapId, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT rp.role AS role, COALESCE(p.alias, rp.raw_name) AS user, " +
                        "COUNT(*) AS total_runs, " +
                        "SUM(CASE WHEN r.end_reason = 'wipe' AND r.completed = FALSE AND EXISTS (" +
                        "    SELECT 1 FROM run_objectives ro " +
                        "    JOIN role_objectives rol ON rol.map_id = r.map_id AND rol.objective_name = ro.name AND rol.role = rp.role " +
                        "    WHERE ro.run_id = r.id AND ro.status = 1" +
                        ") THEN 1 ELSE 0 END) AS fails " +
                        "FROM run_participants rp " +
                        "JOIN runs r ON r.id = rp.run_id " +
                        "LEFT JOIN characters c ON c.id = rp.character_id " +
                        "LEFT JOIN people p ON p.id = c.person_id " +
                        "WHERE r.map_id = ? AND rp.role IS NOT NULL " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "GROUP BY rp.role, COALESCE(p.alias, rp.raw_name) " +
                        "ORDER BY fails DESC",
                (rs, rowNum) -> {
                    long totalRuns = rs.getLong("total_runs");
                    long fails = rs.getLong("fails");
                    double percentage = totalRuns == 0 ? 0.0 : (fails * 100.0) / totalRuns;
                    return new RoleUserFailResponse(rs.getString("role"), rs.getString("user"), totalRuns, fails, percentage);
                },
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
    }

    /** One row per user who has ever participated on this map — "Global fails" (resigns), not role-scoped. */
    public List<UserResignResponse> findGlobalFails(Integer mapId, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT COALESCE(p.alias, rp.raw_name) AS user, " +
                        "COUNT(*) AS total_runs, " +
                        "SUM(CASE WHEN r.end_reason = 'resign' AND r.completed = FALSE THEN 1 ELSE 0 END) AS resigns " +
                        "FROM run_participants rp " +
                        "JOIN runs r ON r.id = rp.run_id " +
                        "LEFT JOIN characters c ON c.id = rp.character_id " +
                        "LEFT JOIN people p ON p.id = c.person_id " +
                        "WHERE r.map_id = ? " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "GROUP BY COALESCE(p.alias, rp.raw_name) " +
                        "ORDER BY resigns DESC",
                (rs, rowNum) -> {
                    long totalRuns = rs.getLong("total_runs");
                    long resigns = rs.getLong("resigns");
                    double percentage = totalRuns == 0 ? 0.0 : (resigns * 100.0) / totalRuns;
                    return new UserResignResponse(rs.getString("user"), totalRuns, resigns, percentage);
                },
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
    }

    /**
     * One row per (role, user) that has ever played that role on this map, deaths summed, worst
     * first. Not scoped to completed runs — a death counts whether the run wiped or succeeded.
     * Same {@code COALESCE(alias, raw_name)}/null-role exclusion as {@link #findRoleFails}.
     */
    public List<RoleUserDeathsResponse> findRoleDeaths(Integer mapId, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT rp.role AS role, COALESCE(p.alias, rp.raw_name) AS user, " +
                        "COUNT(*) AS total_runs, SUM(rp.deaths) AS total_deaths " +
                        "FROM run_participants rp " +
                        "JOIN runs r ON r.id = rp.run_id " +
                        "LEFT JOIN characters c ON c.id = rp.character_id " +
                        "LEFT JOIN people p ON p.id = c.person_id " +
                        "WHERE r.map_id = ? AND rp.role IS NOT NULL " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "GROUP BY rp.role, COALESCE(p.alias, rp.raw_name) " +
                        "ORDER BY total_deaths DESC",
                (rs, rowNum) -> {
                    long totalRuns = rs.getLong("total_runs");
                    long deaths = rs.getLong("total_deaths");
                    double avgDeaths = totalRuns == 0 ? 0.0 : ((double) deaths) / totalRuns;
                    return new RoleUserDeathsResponse(rs.getString("role"), rs.getString("user"), totalRuns, deaths, avgDeaths);
                },
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
    }

    /** {@code java.time.Instant} isn't one of JDBC 4.2's mandated {@code setObject} conversions; convert explicitly. */
    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
