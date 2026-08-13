package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    /** Backs the "Admin" column in AdminUserController's listing — existsById per row would be N+1. */
    @Query("select a.personId from Admin a")
    Set<Long> findAllPersonIds();
}
