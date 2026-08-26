package com.howl.uwtracker.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Supplies the {@link TaskScheduler} shared by every in-memory run-voting registry (currently
 * {@code FailureReportVotingRegistry} and {@code MvpReportVotingRegistry}) to close each run's
 * voting window — Spring Boot doesn't auto-configure a TaskScheduler bean on its own (that only
 * happens once something enables {@code @Scheduled} processing), so this app needs to provide one
 * explicitly to have anything to inject. Kept in com.howl.uwtracker.web rather than either feature
 * package, same reasoning as {@link MachineKeyAuthenticationService} — it's genuinely shared
 * infrastructure, not owned by one vote type. A small fixed pool is plenty: each task is a quick
 * tally + a couple of inserts, and "a handful of concurrent uploads at most" (see MapDedupLock)
 * means very few windows of either kind are ever open at once.
 */
@Configuration
public class RunVoteSchedulingConfig {

    @Bean
    public TaskScheduler runVoteTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("run-vote-");
        scheduler.initialize();
        return scheduler;
    }
}
