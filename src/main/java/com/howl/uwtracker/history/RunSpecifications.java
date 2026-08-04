package com.howl.uwtracker.history;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunParticipant;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * specs/backend/06-run-history.md — combinable filter predicates, only applied when the caller
 * supplies them. Public: also reused by {@code LeaderboardService} to apply the same map/completed/
 * date-range predicates to the leaderboard queries (specs/frontend's time-window filter).
 */
public final class RunSpecifications {

    private RunSpecifications() {
    }

    public static Specification<Run> hasMap(Integer mapId) {
        if (mapId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("map").get("id"), mapId);
    }

    public static Specification<Run> isCompleted(Boolean completed) {
        if (completed == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("completed"), completed);
    }

    static Specification<Run> hasEndReason(String endReason) {
        if (endReason == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("endReason"), endReason);
    }

    public static Specification<Run> startedBetween(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("utcStart"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("utcStart"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** person/character/role all filter through run_participants — combined into one EXISTS subquery. */
    static Specification<Run> hasParticipantMatching(Long personId, Long characterId, String role) {
        if (personId == null && characterId == null && role == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var rp = subquery.from(RunParticipant.class);
            subquery.select(rp.get("id"));

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(rp.get("run"), root));
            if (characterId != null) {
                predicates.add(cb.equal(rp.get("character").get("id"), characterId));
            }
            if (personId != null) {
                predicates.add(cb.equal(rp.get("character").get("person").get("id"), personId));
            }
            if (role != null) {
                predicates.add(cb.equal(rp.get("role"), role));
            }
            subquery.where(predicates.toArray(new Predicate[0]));

            return cb.exists(subquery);
        };
    }
}
