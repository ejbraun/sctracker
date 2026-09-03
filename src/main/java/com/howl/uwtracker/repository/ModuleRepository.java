package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * The artifact registry — see specs/backend/08-module-entitlements.md. Rows are managed through the
 * admin module API (AdminModuleController); the three public rows (sctracker, pp-exe, pp-base) are
 * seeded by changeset 045.
 */
public interface ModuleRepository extends JpaRepository<Module, Long> {

    Optional<Module> findByModuleKey(String moduleKey);

    boolean existsByModuleKey(String moduleKey);

    /** Enabled modules in display order — powers {@code GET /artifacts} and the admin grant checklist. */
    List<Module> findByEnabledTrueOrderBySortOrderAscModuleKeyAsc();
}
