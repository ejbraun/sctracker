package com.howl.uwtracker.failurereports;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Supplies the {@link TaskScheduler} {@link FailureReportVotingRegistry} uses to close each run's
 * voting window — Spring Boot doesn't auto-configure a TaskScheduler bean on its own (that only
 * happens once something enables {@code @Scheduled} processing), so this app needs to provide one
 * explicitly to have anything to inject. A small fixed pool is plenty: each task is a quick tally +
 * a couple of inserts, and "a handful of concurrent uploads at most" (see MapDedupLock) means very
 * few windows are ever open at once.
 */
@Configuration
public class FailureReportSchedulingConfig {

    @Bean
    public TaskScheduler failureReportTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("failure-report-voting-");
        scheduler.initialize();
        return scheduler;
    }
}
