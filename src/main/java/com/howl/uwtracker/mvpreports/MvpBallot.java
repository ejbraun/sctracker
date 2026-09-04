package com.howl.uwtracker.mvpreports;

import java.util.Set;

/**
 * One reporter's exact MVP submission for a run: either the single target they credit, or
 * "Nobody" — mirrors {@code ReportRunMvpRequest}'s own single-select rule (see MvpReportService).
 * {@code targets} holds 0 or 1 entries, never more — MvpReportService rejects anything larger
 * before this is ever constructed. Holds a role name for a role-based run's config, or a character
 * {@code raw_name} for a role-less one ({@code MapConfig.roleModel == null}) — which
 * interpretation applies is resolved later, at persist time, by {@link MvpPersister}. A distinct
 * type from failurereports' {@code Ballot} rather than reusing it: the two vote kinds are
 * structurally identical today by coincidence, not by a shared rule that needs to stay in sync, so
 * keeping them independent means one can evolve (e.g. gain a confidence field) without dragging the
 * other along. Used as an {@code MvpReportPersister} map key as-is, since {@code Set}'s
 * equals/hashCode is content-based.
 */
public record MvpBallot(boolean nobody, Set<String> targets) {

    public MvpBallot {
        targets = Set.copyOf(targets);
    }
}
