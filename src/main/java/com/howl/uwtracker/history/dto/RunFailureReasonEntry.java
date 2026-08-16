package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.RunFailureReason;
import com.howl.uwtracker.domain.RunParticipant;

/**
 * A structural {@code nobody} flag, not a "Nobody" sentinel string in {@code displayName} — a real
 * character could plausibly be named "Nobody", which a string-matched sentinel would collide with.
 * {@code role}/{@code displayName} are null when {@code nobody} is true.
 */
public record RunFailureReasonEntry(boolean nobody, String role, String displayName) {

    public static RunFailureReasonEntry from(RunFailureReason reason) {
        RunParticipant rp = reason.getRunParticipant();
        if (rp == null) {
            return new RunFailureReasonEntry(true, null, null);
        }
        String name = rp.getCharacter() == null ? rp.getRawName() : rp.getCharacter().getCharacterName();
        return new RunFailureReasonEntry(false, rp.getRole(), name);
    }
}
