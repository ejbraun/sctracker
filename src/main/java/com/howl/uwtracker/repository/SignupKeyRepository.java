package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.SignupKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SignupKeyRepository extends JpaRepository<SignupKey, Long> {

    Optional<SignupKey> findByKeyHashAndUsedAtIsNull(String keyHash);
}
