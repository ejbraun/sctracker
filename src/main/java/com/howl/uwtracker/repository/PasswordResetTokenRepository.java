package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Redeem the token with this hash: mark it used iff it is unused and unexpired. The conditional
     * UPDATE makes it atomic — two concurrent redemptions can't both win. Returns the rows updated:
     * {@code 1} = claimed, {@code 0} = unknown / already used / expired.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int tryClaim(@Param("hash") String hash, @Param("now") Instant now);

    /** Kills every still-live link for a person, so minting a new one supersedes the old ones. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.personId = :personId and t.usedAt is null")
    int invalidateOutstanding(@Param("personId") Long personId, @Param("now") Instant now);
}
