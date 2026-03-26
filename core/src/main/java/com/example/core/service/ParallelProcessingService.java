package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.pipeline.ProviderLimitException;
import com.example.core.pipeline.RepairPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service for processing commits in parallel using a thread pool.
 * Handles extraction, classification, and repair pipeline execution.
 */
public class ParallelProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ParallelProcessingService.class);

    private final int threadCount;
    private final ExecutorService executor;
    private final boolean verbose;
    private final Long commitTimeoutMinutes; // null = sin timeout (ilimitado)

    // Thread-safe counters for progress tracking
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    // Global stop flag: set to true when a ProviderLimitException is detected
    // to prevent remaining commits from consuming API credits.
    private final AtomicBoolean globalStop = new AtomicBoolean(false);

    public ParallelProcessingService(int threadCount, boolean verbose, Long commitTimeoutMinutes) {
        this.threadCount = threadCount;
        this.verbose = verbose;
        this.commitTimeoutMinutes = commitTimeoutMinutes; // null = sin timeout
        this.executor = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(false);
                t.setName("commit-processor-" + (counter++));
                return t;
            }
        });
        if (commitTimeoutMinutes != null) {
            log.info("Initialized ParallelProcessingService with {} threads, timeout: {} minutes",
                    threadCount, commitTimeoutMinutes);
        } else {
            log.info("Initialized ParallelProcessingService with {} threads, no timeout (unlimited)",
                    threadCount);
        }
    }

    /**
     * Processes commits in parallel.
     *
     * @param records the list of breaking update records to process
     * @param outputBaseDir the base output directory
     * @param extractJarsAndClassify whether to extract JARs and run classifier
     * @param cleanExisting whether clean mode is enabled
     * @param summaryConsumer consumer for processing summaries (e.g., writing JSON)
     * @param repairPipeline the repair pipeline to execute (can be null)
     * @param recordByCommit map of records by commit hash
     * @param jsonOutputPath path to JSON output file (for repair pipeline)
     * @return list of classification summaries
     */
    public List<ClassificationSummary> processCommitsInParallel(
            List<BreakingUpdateRecord> records,
            Path outputBaseDir,
            boolean extractJarsAndClassify,
            boolean cleanExisting,
            Consumer<ClassificationSummary> summaryConsumer,
            RepairPipeline repairPipeline,
            java.util.Map<String, BreakingUpdateRecord> recordByCommit,
            Path jsonOutputPath,
            com.example.core.service.ChangeImpactReportService changeImpactReportService) {

        log.info("Starting parallel processing of {} commits with {} threads", records.size(), threadCount);
        System.out.println("\n=== Parallel Processing ===");
        System.out.println("Threads: " + threadCount);
        System.out.println("Commits: " + records.size());
        if (commitTimeoutMinutes != null) {
            System.out.println("Timeout per commit: " + commitTimeoutMinutes + " minutes");
        } else {
            System.out.println("Timeout per commit: unlimited (no timeout)");
        }
        System.out.println();

        // Use CompletionService so results are collected in completion order (first-done-first-served).
        // This ensures commits that finish early are written to JSON immediately without waiting
        // for slower commits submitted before them.
        CompletionService<ClassificationSummary> completionService =
                new ExecutorCompletionService<>(executor);

        // Keep track of submitted futures so we can cancel them on ProviderLimitException.
        List<Future<ClassificationSummary>> submittedFutures = new ArrayList<>(records.size());

        // Submit all tasks
        for (BreakingUpdateRecord record : records) {
            CommitProcessor processor = new CommitProcessor(
                    record,
                    outputBaseDir,
                    extractJarsAndClassify,
                    cleanExisting,
                    repairPipeline,
                    recordByCommit,
                    jsonOutputPath,
                    verbose,
                    changeImpactReportService
            );

            Future<ClassificationSummary> future = completionService.submit(processor);
            submittedFutures.add(future);
        }

        // Collect results in completion order
        List<ClassificationSummary> summaries = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {

            // If a ProviderLimitException was detected, cancel all remaining futures and stop.
            if (globalStop.get()) {
                log.warn("Global stop triggered — cancelling {} pending futures", records.size() - i);
                for (Future<ClassificationSummary> f : submittedFutures) {
                    f.cancel(true);
                }
                break;
            }

            try {
                Future<ClassificationSummary> future;
                if (commitTimeoutMinutes != null) {
                    future = completionService.poll(commitTimeoutMinutes, TimeUnit.MINUTES);
                } else {
                    future = completionService.take(); // blocks until any task completes
                }

                if (future == null) {
                    // poll() returned null: timeout elapsed with no completed task
                    failureCount.incrementAndGet();
                    completedCount.incrementAndGet();
                    log.error("Timeout: no commit completed within {} minutes", commitTimeoutMinutes);
                    // We cannot associate the timeout with a specific record here (CompletionService
                    // doesn't track which record belongs to which future), so we log and continue.
                    continue;
                }

                ClassificationSummary summary = future.get();

                if (summary != null) {
                    summaries.add(summary);
                    if (summaryConsumer != null) {
                        summaryConsumer.accept(summary);
                    }
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                    log.warn("Processing returned null for a commit");
                }
                completedCount.incrementAndGet();

            } catch (ExecutionException e) {
                completedCount.incrementAndGet();
                Throwable cause = e.getCause();

                // ProviderLimitException: stop everything — remaining commits would fail anyway
                // and continuing would waste API credits.
                if (cause instanceof ProviderLimitException) {
                    log.error("ProviderLimitException detected — activating global stop: {}", cause.getMessage());
                    globalStop.set(true);
                    failureCount.incrementAndGet();
                    for (Future<ClassificationSummary> f : submittedFutures) {
                        f.cancel(true);
                    }
                    break;
                }

                failureCount.incrementAndGet();
                log.error("Error processing a commit: {}",
                        cause != null ? cause.getMessage() : e.getMessage(), cause);

                // Emit a failure summary so the JSON report is still updated
                if (summaryConsumer != null) {
                    try {
                        // We cannot recover the specific record here; log the error only.
                        log.warn("Failure summary not written because commit identity is unavailable from CompletionService.");
                    } catch (Exception consumerError) {
                        log.error("Error while consuming error summary: {}", consumerError.getMessage(), consumerError);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for commit result: {}", e.getMessage());
                failureCount.incrementAndGet();
                break;
            }
        }

        // Print summary
        System.out.println("\n=== Parallel Processing Summary ===");
        System.out.println("Successfully processed: " + successCount.get());
        System.out.println("Failed: " + failureCount.get());
        System.out.println("Total: " + records.size());
        System.out.println();

        return summaries;
    }

    /**
     * Shuts down the executor service.
     * Should be called when done processing.
     */
    public void shutdown() {
        log.info("Shutting down ParallelProcessingService");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
