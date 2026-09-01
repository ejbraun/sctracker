package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByAlias(String alias);

    /** Backs the Run History "person" filter dropdown — only people who've bothered to set one are choosable. */
    List<Person> findByAliasIsNotNullOrderByAliasAsc();

    /**
     * Records that a machine key belonging to this person just authenticated, and the
     * {@code X-Plugin-Version} it carried ({@code null} for a client too old to send the header).
     * A single targeted UPDATE, in its own {@code REQUIRES_NEW} transaction: callers range from no
     * transaction ({@code /can-report-run-failure}) to a read-only one ({@code MvpReportService}
     * wraps {@code authenticate} in {@code @Transactional(readOnly = true)}), and the sighting must
     * commit either way — including when the surrounding request then 426s. Backs the "Players On
     * An Outdated Plugin" Loserboards widget.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update Person p set p.lastPluginSeenAt = :seenAt, p.lastSeenPluginVersion = :version where p.id = :id")
    void recordPluginSeen(@Param("id") Long id, @Param("seenAt") Instant seenAt, @Param("version") Integer version);
}
