package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.OutdatedUploadAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutdatedUploadAttemptRepository extends JpaRepository<OutdatedUploadAttempt, Long> {
}
