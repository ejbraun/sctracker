package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.PersonModuleGrant;
import com.howl.uwtracker.domain.PersonModuleGrantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

public interface PersonModuleGrantRepository extends JpaRepository<PersonModuleGrant, PersonModuleGrantId> {

    List<PersonModuleGrant> findByIdPersonId(Long personId);

    boolean existsByIdPersonIdAndIdModuleId(Long personId, Long moduleId);

    /** Idempotent revoke; annotated so it can be called outside an ambient transaction (e.g. tests). */
    @Transactional
    void deleteByIdPersonIdAndIdModuleId(Long personId, Long moduleId);

    /** Module ids this person can access — the fast path for {@code GET /module-entitlements}. */
    @Query("select g.id.moduleId from PersonModuleGrant g where g.id.personId = ?1")
    Set<Long> findModuleIdsByPersonId(Long personId);
}
