package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Profession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionRepository extends JpaRepository<Profession, Integer> {
}
