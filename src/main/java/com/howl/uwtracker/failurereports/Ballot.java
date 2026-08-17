package com.howl.uwtracker.failurereports;

import java.util.Set;

/**
 * One reporter's exact submission for a run: either a set of roles they blame, or "Nobody" —
 * mirrors ReportRunFailureRequest's own exclusivity rule (see FailureReportService). Used as a
 * FailureReportPersister map key as-is, since Set&lt;String&gt;'s equals/hashCode is content-based
 * regardless of iteration order — two reporters naming the same roles in a different order still
 * produce equal ballots and get tallied together.
 */
public record Ballot(boolean nobody, Set<String> roles) {

    public Ballot {
        roles = Set.copyOf(roles);
    }
}
