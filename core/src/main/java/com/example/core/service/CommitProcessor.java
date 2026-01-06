package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.pipeline.ProjectLogLocator;
import com.example.core.pipeline.RepairPipeline;
import com.example.core.util.ProjectPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.Attempt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Processes a single commit (extraction, classification, and repair pipeline).
 * This class is designed to be executed in parallel by ParallelProcessingService.
 */
public class CommitProcessor implements Callable<ClassificationSummary> {

    private static final Logger log = LoggerFactory.getLogger(CommitProcessor.class);

    private final BreakingUpdateRecord record;
    private final BreakingUpdateExtractionService extractionService;
    private final Path outputBaseDir;
    private final boolean extractJarsAndClassify;
    private final boolean cleanExisting;
    private final RepairPipeline repairPipeline;
    private final Path jsonOutputPath;
    private final ChangeImpactReportService changeImpactReportService;
    private final boolean verbose;
    private final Consumer<Boolean> progressCallback;

    public CommitProcessor(
            BreakingUpdateRecord record,
            BreakingUpdateExtractionService extractionService,
            Path outputBaseDir,
            boolean extractJarsAndClassify,
            boolean cleanExisting,
            RepairPipeline repairPipeline,
            Map<String, BreakingUpdateRecord> recordByCommit,
            Path jsonOutputPath,
            boolean verbose,
            ChangeImpactReportService changeImpactReportService,
            Consumer<Boolean> progressCallback) {
        this.record = record;
        this.extractionService = extractionService;
        this.outputBaseDir = outputBaseDir;
        this.extractJarsAndClassify = extractJarsAndClassify;
        this.cleanExisting = cleanExisting;
        this.repairPipeline = repairPipeline;
        this.jsonOutputPath = jsonOutputPath;
        this.changeImpactReportService = changeImpactReportService;
        this.verbose = verbose;
        this.progressCallback = progressCallback;
    }

    @Override
    public ClassificationSummary call() throws Exception {
        String commit = record.breakingCommit();
        String threadName = Thread.currentThread().getName();
        
        log.info("[{}] Starting processing commit: {}", threadName, commit);
        
        try {
            // Step 1: Extract and classify (equivalent to processSingleCommit)
            ClassificationSummary summary = extractionService.processSingleCommit(
                    record,
                    outputBaseDir,
                    extractJarsAndClassify,
                    cleanExisting
            );

            if (summary == null) {
                log.warn("[{}] Failed to extract/classify commit: {}", threadName, commit);
                if (progressCallback != null) {
                    progressCallback.accept(false);
                }
                return null;
            }

            log.info("[{}] Extraction/classification completed for commit: {}", threadName, commit);

            // Step 2: Execute repair pipeline if available
            // This is equivalent to what writePerCommitReport() does
            if (repairPipeline != null && summary.dockerImage() != null) {
                try {
                    Path commitOutputDir = outputBaseDir.resolve(commit);
                    // Reports are stored in: {jsonOutputPath}/{commit}/ (if jsonOutputPath is set)
                    // Otherwise fallback to: {outputBaseDir}/{commit}/reports/{commit}
                    Path commitDir = jsonOutputPath != null && jsonOutputPath.getParent() != null
                            ? jsonOutputPath.getParent().resolve(commit)
                            : commitOutputDir.resolve("reports").resolve(commit);
                    Files.createDirectories(commitDir);

                    // Find initial log file
                    Path projectDir = ProjectPaths.resolveProjectDir(commitOutputDir, record.project());
                    Path initialLogFile = null;
                    Path initialClassifierReport = null;
                    
                    if (Files.exists(projectDir)) {
                        initialLogFile = ProjectLogLocator.findLogFile(
                                projectDir, record.project(), commit);
                        
                        // Generate breaking-classifier-report.json BEFORE calling repair pipeline
                        // This is needed because AgentRepairPipeline expects it to exist
                        if (initialLogFile != null && Files.exists(initialLogFile)) {
                            try {
                                initialClassifierReport = commitDir.resolve("breaking-classifier-report.json");
                                github.chains.breakingclassifier.BreakingClassifierApp classifierApp = 
                                        new github.chains.breakingclassifier.BreakingClassifierApp();
                                github.chains.breakingclassifier.BreakingReport breakingReport = 
                                        classifierApp.analyzeLog(initialLogFile, initialClassifierReport);
                                
                                if (breakingReport != null) {
                                    log.info("[{}] Generated breaking-classifier-report.json. Category: {}, Files with errors: {}", 
                                            threadName, breakingReport.failureCategory(), breakingReport.errorsByFile().size());
                                }
                            } catch (Exception e) {
                                log.warn("[{}] Failed to generate breaking-classifier-report.json: {}", 
                                        threadName, e.getMessage());
                                initialClassifierReport = null;
                            }
                        } else {
                            log.warn("[{}] Initial log file not found in project directory: {}", threadName, projectDir);
                        }
                    }

                    // Generate breaking-changes.json (STEP 2: Initial Analysis)
                    // This is equivalent to writePerCommitReport() STEP 2
                    // This generates breaking-changes.json (once per commit, based on japicmp)
                    if (changeImpactReportService != null && initialClassifierReport != null && Files.exists(initialClassifierReport)) {
                        try {
                            changeImpactReportService.copyAndGenerate(record, summary, outputBaseDir, commitDir,
                                    initialClassifierReport);
                            log.info("[{}] Generated breaking-changes.json for commit: {}", threadName, commit);
                        } catch (Exception e) {
                            log.warn("[{}] Failed to generate breaking-changes.json for commit {}: {}", 
                                    threadName, commit, e.getMessage());
                            if (verbose) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        if (changeImpactReportService == null) {
                            log.debug("[{}] ChangeImpactReportService not available, skipping breaking-changes.json", threadName);
                        } else if (initialClassifierReport == null || !Files.exists(initialClassifierReport)) {
                            log.debug("[{}] breaking-classifier-report.json not available, skipping breaking-changes.json", threadName);
                        }
                    }

                    log.info("[{}] Executing repair pipeline for commit: {}", threadName, commit);
                    
                    // Execute repair pipeline (this is the critical part that needs to be parallelized)
                    List<Attempt> attempts = repairPipeline.runRepairLoop(
                            commitOutputDir,
                            record.project(),
                            summary.dockerImage(),
                            record,
                            commitDir,
                            summary,
                            outputBaseDir,
                            initialLogFile
                    );

                    // Update summary with attempts
                    if (attempts != null && !attempts.isEmpty()) {
                        List<Attempt> updatedAttempts = new ArrayList<>();
                        if (summary.attempts() != null) {
                            updatedAttempts.addAll(summary.attempts());
                        }
                        updatedAttempts.addAll(attempts);

                        // Get the last attempt's category
                        Attempt lastAttempt = attempts.get(attempts.size() - 1);
                        String finalCategory = lastAttempt.getFailureCategory().toString();
                        String finalLogFile = summary.logFile();
                        
                        // Try to find the last attempt's log file
                        Path lastLogFile = commitDir.resolve("attempt_" + lastAttempt.getAttemptCount() + "_build.log");
                        if (Files.exists(lastLogFile)) {
                            finalLogFile = lastLogFile.toString();
                        }

                        summary = new ClassificationSummary(
                                summary.project(),
                                summary.breakingCommit(),
                                summary.datasetCategory(),
                                finalCategory,
                                finalLogFile != null ? finalLogFile : summary.logFile(),
                                summary.classifierReport(),
                                summary.dockerImage(),
                                updatedAttempts
                        );

                        log.info("[{}] Repair pipeline completed for commit: {} ({} attempts)", 
                                threadName, commit, attempts.size());
                    } else {
                        log.warn("[{}] Repair pipeline returned no attempts for commit: {}", threadName, commit);
                    }
                    
                } catch (Exception e) {
                    log.error("[{}] Error in repair pipeline for commit {}: {}", 
                            threadName, commit, e.getMessage(), e);
                    // Continue with summary without repair attempts
                }
            } else {
                if (repairPipeline == null) {
                    log.debug("[{}] Repair pipeline not available for commit: {}", threadName, commit);
                } else {
                    log.debug("[{}] Docker image not available for repair pipeline (commit: {})", threadName, commit);
                }
            }

            if (progressCallback != null) {
                progressCallback.accept(true);
            }

            log.info("[{}] Completed processing commit: {}", threadName, commit);
            return summary;

        } catch (Exception e) {
            log.error("[{}] Fatal error processing commit {}: {}", threadName, commit, e.getMessage(), e);
            if (progressCallback != null) {
                progressCallback.accept(false);
            }
            throw e;
        }
    }
}

