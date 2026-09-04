package com.howl.uwtracker.failurereports;

import java.util.Set;

/**
 * One reporter's exact submission for a run: either a set of targets they blame, or "Nobody" —
 * mirrors ReportRunFailureRequest's own exclusivity rule (see FailureReportService). {@code
 * targets} holds role names for a role-based run's config, or character {@code raw_name}s for a
 * role-less one ({@code MapConfig.roleModel == null}) — which interpretation applies is resolved
 * later, at persist time, by {@link FailureReportPersister}; this type is just a content-addressed
 * bag of strings either way. Used as a FailureReportPersister map key as-is, since
 * Set&lt;String&gt;'s equals/hashCode is content-based regardless of iteration order — two
 * reporters naming the same targets in a different order still produce equal ballots and get
 * tallied together.
 */
public record Ballot(boolean nobody, Set<String> targets) {

    public Ballot {
        targets = Set.copyOf(targets);
    }
}
