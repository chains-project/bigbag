package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.pipeline.RepairPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
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

        List<Future<ClassificationSummary>> futures = new ArrayList<>();
        BreakingUpdateExtractionService extractionService = new BreakingUpdateExtractionService(verbose);

        // Submit all tasks
        for (BreakingUpdateRecord record : records) {
            CommitProcessor processor = new CommitProcessor(
                    record,
                    extractionService,
                    outputBaseDir,
                    extractJarsAndClassify,
                    cleanExisting,
                    repairPipeline,
                    recordByCommit,
                    jsonOutputPath,
                    verbose,
                    changeImpactReportService,
                    this::updateProgress
            );
            
            Future<ClassificationSummary> future = executor.submit(processor);
            futures.add(future);
        }

        // Collect results
        List<ClassificationSummary> summaries = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Future<ClassificationSummary> future = futures.get(i);
            BreakingUpdateRecord record = records.get(i);
            
            try {
                // Wait for completion (with or without timeout)
                ClassificationSummary summary;
                if (commitTimeoutMinutes != null) {
                    // Con timeout configurado
                    summary = future.get(commitTimeoutMinutes, TimeUnit.MINUTES);
                } else {
                    // Sin timeout - espera indefinidamente
                    summary = future.get();
                }
                
                if (summary != null) {
                    summaries.add(summary);
                    if (summaryConsumer != null) {
                        summaryConsumer.accept(summary);
                    }
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                    log.warn("Processing returned null for commit: {}", record.breakingCommit());
                }
                
            } catch (TimeoutException e) {
                // Solo puede ocurrir si commitTimeoutMinutes != null
                failureCount.incrementAndGet();
                log.error("Timeout processing commit {} after {} minutes", 
                        record.breakingCommit(), commitTimeoutMinutes);
                future.cancel(true);
                
                // Create a failure summary
                ClassificationSummary failureSummary = new ClassificationSummary(
                        record.project(),
                        record.breakingCommit(),
                        record.failureCategory(),
                        "TIMEOUT",
                        null,
                        null,
                        null,
                        null
                );
                summaries.add(failureSummary);
                
            } catch (ExecutionException e) {
                failureCount.incrementAndGet();
                Throwable cause = e.getCause();
                log.error("Error processing commit {}: {}", 
                        record.breakingCommit(), cause != null ? cause.getMessage() : e.getMessage(), cause);
                
                // Create a failure summary
                ClassificationSummary failureSummary = new ClassificationSummary(
                        record.project(),
                        record.breakingCommit(),
                        record.failureCategory(),
                        "ERROR",
                        null,
                        null,
                        null,
                        null
                );
                summaries.add(failureSummary);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for commit {}: {}", 
                        record.breakingCommit(), e.getMessage());
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
     * Updates progress counters (thread-safe callback).
     */
    private void updateProgress(boolean success) {
        completedCount.incrementAndGet();
        if (success) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }
        
        if (verbose) {
            int completed = completedCount.get();
            log.debug("Progress: {}/{} completed ({} success, {} failed)", 
                    completed, completedCount.get() + (successCount.get() + failureCount.get() - completed),
                    successCount.get(), failureCount.get());
        }
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

