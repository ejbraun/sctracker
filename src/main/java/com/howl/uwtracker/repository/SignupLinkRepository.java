package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.SignupLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SignupLinkRepository extends JpaRepository<SignupLink, Long> {

    List<SignupLink> findAllByOrderByCreatedAtDesc();

    /**
     * Redeem one signup against the link with this token hash: bump {@code use_count} iff the link
     * is live (not revoked) and hasn't hit {@code max_uses}. The {@code WHERE use_count < max_uses}
     * guard makes it atomic — concurrent signups can't push a 10-use link to 11. Returns the number
     * of rows updated: {@code 1} = claimed, {@code 0} = unknown / revoked / exhausted.
     */
    @Modifying
    @Query("update SignupLink l set l.useCount = l.useCount + 1 "
            + "where l.tokenHash = :hash and l.revokedAt is null and l.useCount < l.maxUses")
    int tryClaim(@Param("hash") String hash);
}
