package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * The artifact registry — see specs/backend/08-module-entitlements.md. Rows are managed through the
 * admin module API (AdminModuleController); only {@code sctracker} is seeded (changeset 045, moved
 * to the {@code plugins/SCTracker/} layout by 046) — every other row, plugins and launcher
 * components alike, is registered at runtime via create or Scan bucket.
 */
public interface ModuleRepository extends JpaRepository<Module, Long> {

    Optional<Module> findByModuleKey(String moduleKey);

    boolean existsByModuleKey(String moduleKey);

    /** Enabled modules in display order — powers {@code GET /artifacts} and the admin grant checklist. */
    List<Module> findByEnabledTrueOrderBySortOrderAscModuleKeyAsc();

    /** Every module, enabled or not, in display order — the admin registry table. */
    List<Module> findAllByOrderBySortOrderAscModuleKeyAsc();
}
