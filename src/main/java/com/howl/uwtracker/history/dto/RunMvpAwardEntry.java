package com.howl.uwtracker.history.dto;

import com.howl.uwtracker.domain.RunMvpAward;
import com.howl.uwtracker.domain.RunParticipant;

/**
 * The run_participant credited as MVP via the plugin's post-run popup, or a deliberate "nobody
 * stood out" assertion — mirrors {@link RunFailureReasonEntry} exactly, just singular (a run has at
 * most one MVP award; see run_mvp_awards' UNIQUE(run_id)) rather than a list. {@code nobody} is
 * structural, not a "Nobody" sentinel string in {@code displayName} — a real character could
 * plausibly be named "Nobody". {@code role}/{@code displayName} are null when {@code nobody} is
 * true. The whole entry is null on {@link RunDetailResponse} when no MVP vote has resolved for this
 * run at all — distinct from an explicit "Nobody" result.
 */
public record RunMvpAwardEntry(boolean nobody, String role, String displayName) {

    public static RunMvpAwardEntry from(RunMvpAward award) {
        RunParticipant rp = award.getRunParticipant();
        if (rp == null) {
            return new RunMvpAwardEntry(true, null, null);
        }
        String name = rp.getCharacter() == null ? rp.getRawName() : rp.getCharacter().getCharacterName();
        return new RunMvpAwardEntry(false, rp.getRole(), name);
    }
}
