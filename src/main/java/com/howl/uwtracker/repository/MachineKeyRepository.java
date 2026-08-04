package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.MachineKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineKeyRepository extends JpaRepository<MachineKey, Long> {

    Optional<MachineKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    List<MachineKey> findByPerson_IdOrderByCreatedAtDesc(Long personId);
}
