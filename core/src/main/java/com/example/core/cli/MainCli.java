package com.example.core.cli;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.pipeline.RepairPipeline;
import com.example.core.pipeline.ModelRepairPipeline;
import com.example.core.pipeline.AgentRepairPipeline;
import com.example.core.prompt.PromptGenerationService;
import com.example.core.report.JsonReportReader;
import com.example.core.service.BreakingUpdateExtractionService;
import com.example.core.service.ChangeImpactReportService;
import com.example.core.service.GitWorkflowService;
import com.example.core.service.ParallelProcessingService;
import com.example.core.util.FileSystemUtils;
import com.example.core.util.ProjectPaths;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import picocli.CommandLine;
import se.kth.DockerBuild;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI command for processing breaking update records from JSON files.
 * Uses Picocli for argument parsing and delegates to service classes for
 * business logic.
 */
@CommandLine.Command(name = "breaking-update-processor", mixinStandardHelpOptions = true, description = "Processes breaking update records from JSON files, extracts projects from Docker images, and optionally runs classification.")
public class MainCli implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(MainCli.class);

    /**
     * Loads commit hashes to skip from the file specified by SKIP_HASHES_FILE in .env.
     * The file should contain one hash per line. Empty lines and lines starting with # are ignored.
     */
    private Set<String> loadSkipHashes() {
        Set<String> skipHashes = new HashSet<>();
        String skipHashesFile = envConfig.get("SKIP_HASHES_FILE").orElse(null);
        if (skipHashesFile == null || skipHashesFile.isBlank() || "/path/to/file".equals(skipHashesFile)) {
            log.info("SKIP_HASHES_FILE not configured. No commits will be skipped.");
            return skipHashes;
        }
        Path path = Path.of(skipHashesFile);
        if (!Files.exists(path)) {
            log.warn("SKIP_HASHES_FILE does not exist: {}. No commits will be skipped.", skipHashesFile);
            return skipHashes;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    skipHashes.add(trimmed);
                }
            }
            log.info("Loaded {} hashes to skip from {}", skipHashes.size(), skipHashesFile);
        } catch (IOException e) {
            log.error("Failed to read SKIP_HASHES_FILE {}: {}", skipHashesFile, e.getMessage());
        }
        return skipHashes;
    }

    // Default configuration values
    private static final String DEFAULT_CATEGORY = "COMPILATION_FAILURE";
    private static final boolean DEFAULT_EXTRACT_PROJECTS = true;
    private static final boolean DEFAULT_EXTRACT_JARS_AND_CLASSIFY = true;
    private static final boolean DEFAULT_CLEAN_EXISTING = true;

    @CommandLine.Mixin
    private MainCliOptions options;

    private final EnvConfig envConfig;
    private boolean verbose;
    private BreakingUpdateExtractionService extractionService;
    private final ChangeImpactReportService changeImpactReportService;
    private final PromptGenerationService promptGenerationService;
    private final GitWorkflowService gitWorkflowService;
    private RepairPipeline repairPipeline;
    private DockerBuild dockerBuild;
    
    // Thread-safety: Lock for JSON file writing
    private final Object jsonWriteLock = new Object();

    public MainCli() {
        this.envConfig = EnvConfig.loadDefault();
        // Services will be reinitialized with verbose flag in call() after determining
        // verbose value
        this.extractionService = new BreakingUpdateExtractionService(false);
        this.changeImpactReportService = new ChangeImpactReportService(false, envConfig);
        this.promptGenerationService = new PromptGenerationService(envConfig);
        this.gitWorkflowService = new GitWorkflowService();
        this.dockerBuild = new DockerBuild(false, false);
        // Pipeline will be reinitialized in call() after determining verbose flag and
        // pipeline type
        // Initialize with default model pipeline for safety
        this.repairPipeline = new ModelRepairPipeline(gitWorkflowService, dockerBuild, envConfig, false);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MainCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // Merge CLI arguments with .env values
            String resolvedInputDir = options.getInputDirStr() != null ? options.getInputDirStr()
                    : envConfig.require("INPUT_DIR");
            String resolvedOutputDir = options.getOutputDirStr() != null ? options.getOutputDirStr()
                    : envConfig.require("OUTPUT_DIR");

            Path inputDir = Paths.get(resolvedInputDir);
            Path outputDir = Paths.get(resolvedOutputDir);

            boolean envExtract = envConfig.getBoolean("EXTRACT").orElse(DEFAULT_EXTRACT_PROJECTS);
            boolean envClassify = envConfig.getBoolean("CLASSIFY").orElse(DEFAULT_EXTRACT_JARS_AND_CLASSIFY);
            boolean envClean = envConfig.getBoolean("CLEAN").orElse(DEFAULT_CLEAN_EXISTING);
            boolean envVerbose = envConfig.getBoolean("VERBOSE").orElse(false);
            boolean envParallel = envConfig.getBoolean("PARALLEL_ENABLED").orElse(true);
            int envParallelThreads = envConfig.get("PARALLEL_THREADS")
                    .map(Integer::parseInt)
                    .orElse(Runtime.getRuntime().availableProcessors());
            // Timeout opcional: null = sin timeout (ilimitado), solo procesa en paralelo
            Long envCommitTimeout = envConfig.get("COMMIT_TIMEOUT_MINUTES")
                    .map(Long::parseLong)
                    .orElse(null); // null = sin timeout, procesa sin límite de tiempo

            // Set verbose from options or environment
            this.verbose = options.isVerbose() || envVerbose;

            // Reinitialize services with verbose flag to control logging (especially Docker
            // image pull)
            boolean keepContainer = envConfig.getBoolean("KEEP_CONTAINER").orElse(false);
            this.extractionService = new BreakingUpdateExtractionService(this.verbose, keepContainer);
            this.dockerBuild = new DockerBuild(false, this.verbose);
            this.dockerBuild.setKeepContainer(keepContainer);
            if (keepContainer) {
                log.info("KEEP_CONTAINER=true: breaking images and agent containers will NOT be removed after execution");
            }

            // Determine pipeline type from CLI argument or environment variable
            String selectedPipelineType = determinePipelineType();
            this.repairPipeline = createRepairPipeline(selectedPipelineType);
            log.info("Using repair pipeline: {}", selectedPipelineType);

            // Configure external library loggers based on verbose flag
            configureExternalLibraryLogging(this.verbose);

            boolean shouldExtract = (options.getExtractProjects() != null ? options.getExtractProjects() : envExtract)
                    && !options.isNoExtract();
            boolean shouldClassify = (options.getExtractJarsAndClassify() != null ? options.getExtractJarsAndClassify()
                    : envClassify)
                    && !options.isNoClassify();
            boolean shouldClean = (options.getCleanExisting() != null ? options.getCleanExisting() : envClean)
                    && !options.isNoClean();

            String singleJsonFile = options.getSingleJsonFile();
            if (singleJsonFile == null) {
                singleJsonFile = envConfig.get("FILE").orElse(null);
            }
            Path jsonOutput = options.getJsonOutput();
            if (jsonOutput == null) {
                jsonOutput = envConfig.getPath("JSON_OUTPUT").orElse(null);
            }

            String modelName = envConfig.get("LLM_MODEL").orElse("default_model");

            // Adjust jsonOutput: JSON_OUTPUT should point to a directory
            // For model pipeline: {jsonOutput}/{model}/breaking-updates-results.json
            // For agent pipeline:
            // {jsonOutput}/repair_pipeline/{AGENT_NAME}/breaking-updates-results.json
            if (jsonOutput != null) {
                // If jsonOutput is a file, use its parent directory
                // If jsonOutput is a directory, use it directly
                Path jsonOutputDir;
                if (Files.exists(jsonOutput) && Files.isRegularFile(jsonOutput)) {
                    // It's a file, use parent directory
                    jsonOutputDir = jsonOutput.getParent();
                    if (jsonOutputDir == null) {
                        jsonOutputDir = Paths.get(".");
                    }
                } else {
                    // It's a directory or doesn't exist yet, use it as directory
                    jsonOutputDir = jsonOutput;
                }

                // Build path based on pipeline type
                if ("agent".equals(selectedPipelineType)) {
                    // For agent pipeline:
                    // repair_pipeline/{AGENT_NAME}/{RULE_GENERATOR}/breaking-updates-results.json (if RULE_GENERATOR exists)
                    // repair_pipeline/{AGENT_NAME}/breaking-updates-results.json (if RULE_GENERATOR does not exist)
                    String agentName = envConfig.get("AGENT_NAME").orElse("unknown");
                    String repairPipelineDir = envConfig.get("REPAIR_PIPELINE").orElse("pipeline");
                    Path basePath = jsonOutputDir.resolve(repairPipelineDir).resolve(agentName);

                    // Only add RULE_GENERATOR to path if it exists
                    String ruleGenerator = envConfig.get("RULE_GENERATOR").orElse(null);
                    if (ruleGenerator != null && !ruleGenerator.isBlank()) {
                        jsonOutput = basePath.resolve(ruleGenerator).resolve("breaking-updates-results.json");
                        log.info("JSON output will be written to: {} (agent pipeline: {} + {})", jsonOutput, agentName, ruleGenerator);
                    } else {
                        jsonOutput = basePath.resolve("breaking-updates-results.json");
                        log.info("JSON output will be written to: {} (agent pipeline: {})", jsonOutput, agentName);
                    }
                } else {
                    // For model pipeline: {model}/{RULE_GENERATOR}/breaking-updates-results.json
                    String ruleGenerator = envConfig.get("RULE_GENERATOR").orElse("spoon");
                    jsonOutput = jsonOutputDir.resolve(modelName).resolve(ruleGenerator)
                            .resolve("breaking-updates-results.json");
                    log.info("JSON output will be written to: {} (model pipeline: {} + {})", jsonOutput, modelName, ruleGenerator);
                }
            }

            String fileToProcess = singleJsonFile != null ? singleJsonFile
                    : envConfig.get("SPECIFIC_FILE").filter(s -> !s.isBlank()).orElse(null);

            String category = options.getCategory();
            if (category == null) {
                category = envConfig.get("CATEGORY").orElse(DEFAULT_CATEGORY);
            }

            FailureCategory filterCategory = category != null
                    ? FailureCategoryUtils.parseFailureCategory(category)
                    : FailureCategoryUtils.parseFailureCategory(DEFAULT_CATEGORY);

            // Validate input directory
            if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
                log.error("Input directory does not exist or is not a directory: {}", inputDir);
                System.err.println("Error: Input directory does not exist: " + inputDir);
                return 1;
            }

            log.info("=== Breaking Update Record Extractor ===");
            log.info("Input directory: {}", inputDir);
            if (verbose) {
                log.info("Output directory: {}", outputDir);
            }
            if (filterCategory != null) {
                log.info("Filter category: {}", filterCategory);
                System.out.println("Filtering by category: " + filterCategory);
            }
            if (fileToProcess != null) {
                log.info("Processing single JSON file: {}", fileToProcess);
                System.out.println("Processing single file: " + fileToProcess + ".json");
            }

            // Read and process JSON files
            JsonReportReader reader = new JsonReportReader();
            List<BreakingUpdateRecord> records;

            if (fileToProcess != null) {
                Path jsonFilePath = inputDir.resolve(fileToProcess + ".json");
                if (!Files.exists(jsonFilePath)) {
                    log.error("JSON file does not exist: {}", jsonFilePath);
                    System.err.println("Error: JSON file does not exist: " + jsonFilePath);
                    return 1;
                }
                records = reader.readFromFile(jsonFilePath);
            } else {
                records = reader.readFromDirectory(inputDir);
            }

            log.info("Found {} breaking update records", records.size());

            // Filter out commits listed in SKIP_HASHES_FILE
            // This filter is only applied when no specific file is specified
            if (fileToProcess == null) {
                Set<String> skipHashes = loadSkipHashes();
                if (!skipHashes.isEmpty()) {
                    int originalSize = records.size();
                    records = records.stream()
                            .filter(record -> record.breakingCommit() == null || !skipHashes.contains(record.breakingCommit()))
                            .collect(Collectors.toList());
                    int skipped = originalSize - records.size();
                    log.info("Skipped {} commits from SKIP_HASHES_FILE ({} remaining from {} total)", skipped, records.size(), originalSize);
                    if (skipped > 0) {
                        System.out.println("Skipped " + skipped + " commits from SKIP_HASHES_FILE (" + records.size()
                                + " remaining from " + originalSize + " total)");
                    }
                }
            } else {
                log.info("Processing specific file, skipping hash filter");
            }

            // Filter by category if specified
            if (filterCategory != null) {
                final FailureCategory finalFilterCategory = filterCategory;
                int sizeBeforeCategoryFilter = records.size();
                records = records.stream()
                        .filter(record -> FailureCategoryUtils.matchesFailureCategory(record, finalFilterCategory))
                        .collect(Collectors.toList());
                log.info("Filtered to {} records matching category: {}", records.size(), filterCategory);
                System.out.println("\n=== Breaking Update Records ===");
                System.out.println("Total records found: " + sizeBeforeCategoryFilter);
                System.out.println("Records matching category '" + filterCategory + "': " + records.size());
            } else {
                System.out.println("\n=== Breaking Update Records ===");
                System.out.println("Total records found: " + records.size());
            }
            System.out.println();

            // Display information from each record
            for (int i = 0; i < records.size(); i++) {
                BreakingUpdateRecord record = records.get(i);
                if (verbose) {
                    displayRecordInfo(i + 1, record);
                } else {
                    System.out.println((i + 1) + ". " + record.project() + " - " + record.breakingCommit());
                }
            }

            if (!verbose && records.size() > 0) {
                System.out.println("\nUse --verbose to see detailed information for each record");
            }

            // Always load existing results from JSON report if it exists
            // If the file doesn't exist, it means this is the first execution - this is normal
            final Map<String, ReportEntry> existingResults = new LinkedHashMap<>();
            Set<String> processedCommits = new HashSet<>();
            if (jsonOutput != null && Files.exists(jsonOutput)) {
                Map<String, ReportEntry> loaded = loadExistingResults(jsonOutput);
                existingResults.putAll(loaded);
                processedCommits = new HashSet<>(existingResults.keySet());
                log.info("Loaded {} existing results from {}", existingResults.size(), jsonOutput);
            } else if (jsonOutput != null) {
                log.debug("No existing results file found at {} - this is the first execution", jsonOutput);
            }

            // Check if FILTER option is enabled
            boolean filterEnabled = envConfig.getBoolean("FILTER").orElse(false);

            // If FILTER is enabled, only process commits that are NOT in the report
            List<BreakingUpdateRecord> recordsToProcess = records;
            if (filterEnabled) {
                if (!processedCommits.isEmpty()) {
                    int originalSize = records.size();
                    recordsToProcess = filterRecords(records, processedCommits);
                    log.info("Filter mode enabled: Skipping {} already processed commits. Processing {} new commits (from {} total).",
                            processedCommits.size(), recordsToProcess.size(), originalSize);
                    System.out.println("Filter mode enabled: Skipping " + processedCommits.size() + 
                            " already processed commits. Processing " + recordsToProcess.size() + " new commits.");
                    
                    if (recordsToProcess.isEmpty()) {
                        log.info("All commits have already been processed. Nothing to do.");
                        System.out.println("All commits have already been processed. Nothing to do.");
                        return 0;
                    }
                } else {
                    log.info("Filter mode enabled but no existing results found. Processing all commits.");
                }
            }

            // Build record index from filtered records
            final Map<String, BreakingUpdateRecord> recordByCommit = buildRecordIndex(recordsToProcess);

            // If clean mode is enabled, remove existing folders BEFORE processing
            // If FILTER is enabled, only clean commits that will be processed (preserve existing)
            // Always use recordsToProcess to ensure we clean only what will be processed
            if (shouldClean) {
                if (filterEnabled && !processedCommits.isEmpty()) {
                    // Only clean commits that will be processed (not the ones already in report)
                    System.out.println("Clean mode enabled with filter: Removing folders for commits to be processed (preserving already processed)...");
                    extractionService.cleanExistingFolders(recordsToProcess, outputDir, jsonOutput != null ? jsonOutput.getParent() : null);
                    
                    if (jsonOutput != null) {
                        // Only clean JSON entries for commits that will be processed
                        cleanReportsAndJson(recordsToProcess, jsonOutput);
                    }
                } else {
                    // Normal clean behavior: clean commits that will be processed
                    // Use recordsToProcess (which equals records when FILTER is disabled) to ensure
                    // we clean only the commits that will actually be processed
                    System.out.println(
                            "Clean mode enabled: Removing existing {breakingCommit} folders and reports before processing...");
                    extractionService.cleanExistingFolders(recordsToProcess, outputDir, jsonOutput != null ? jsonOutput.getParent() : null);

                    if (jsonOutput != null) {
                        cleanReportsAndJson(recordsToProcess, jsonOutput);
                    }
                }
            }

            Consumer<ClassificationSummary> summaryConsumer = null;
            if (jsonOutput != null) {
                final Path finalJsonOutput = jsonOutput;
                summaryConsumer = summary -> writeClassificationSummary(
                        finalJsonOutput,
                        summary,
                        recordByCommit,
                        outputDir,
                        existingResults,
                        filterEnabled,
                        shouldClean
                );
            }

            // Extract or classify depending on requested actions
            List<ClassificationSummary> classificationSummaries = java.util.Collections.emptyList();
            
            // Determine if we should use parallel processing
            // Parallel processing is enabled by default, except when processing a single specific file
            boolean useParallel = envParallel && fileToProcess == null;
            
            if (useParallel) {
                // Parallel processing mode (always enabled unless processing single file)
                System.out.println("\n=== Parallel Processing Mode ===");
                log.info("Parallel processing enabled with {} threads", envParallelThreads);
                
                ParallelProcessingService parallelService = new ParallelProcessingService(
                        envParallelThreads, 
                        this.verbose, 
                        envCommitTimeout
                );
                
                try {
                    classificationSummaries = parallelService.processCommitsInParallel(
                            recordsToProcess,
                            outputDir,
                            shouldClassify,
                            shouldClean,
                            summaryConsumer,
                            repairPipeline,
                            recordByCommit,
                            jsonOutput,
                            changeImpactReportService
                    );
                } finally {
                    parallelService.shutdown();
                }
            } else {
                // Sequential processing (when parallel is disabled or processing single file)
                if (shouldExtract) {
                    log.info("=== Starting Project Extraction (Sequential) ===");
                    System.out.println("\n=== Extracting Projects from Docker Images (Sequential) ===");
                    classificationSummaries = extractionService.extractProjectsFromDockerImages(
                            recordsToProcess,
                            outputDir,
                            shouldClassify,
                            shouldClean,
                            summaryConsumer,
                            repairPipeline,
                            recordByCommit,
                            jsonOutput,
                            changeImpactReportService
                    );
                } else if (shouldClassify) {
                    log.info("=== Classifying Existing Projects (Sequential) ===");
                    System.out.println("\n=== Classifying Existing Projects (Sequential) ===");
                    classificationSummaries = extractionService.classifyExistingProjects(
                            recordsToProcess, 
                            outputDir,
                            summaryConsumer,
                            repairPipeline,
                            recordByCommit,
                            jsonOutput,
                            changeImpactReportService
                    );
                }
            }

            if (jsonOutput != null && (classificationSummaries == null || classificationSummaries.isEmpty())) {
                List<ClassificationSummary> datasetSummaries = buildDatasetOnlySummaries(recordsToProcess);
                datasetSummaries.forEach(summaryConsumer);
            }

            log.info("=== Processing Complete ===");
            System.out.println("\n=== Processing Complete ===");

            return 0;

        } catch (IOException e) {
            log.error("Error reading breaking update records", e);
            System.err.println("Error: Failed to read breaking update records: " + e.getMessage());
            e.printStackTrace();
            return 1;
        } catch (Exception e) {
            log.error("Unexpected error", e);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Displays information from a BreakingUpdateRecord.
     *
     * @param index  the record index
     * @param record the breaking update record
     */
    private void displayRecordInfo(int index, BreakingUpdateRecord record) {
        System.out.println("--- Record #" + index + " ---");
        System.out.println("Project: " + (record.project() != null ? record.project() : "N/A"));
        System.out.println("Breaking Commit: " + (record.breakingCommit() != null ? record.breakingCommit() : "N/A"));
        System.out.println("URL: " + (record.url() != null ? record.url() : "N/A"));
        System.out
                .println("Failure Category: " + (record.failureCategory() != null ? record.failureCategory() : "N/A"));

        if (record.updatedDependency() != null) {
            System.out.println("Updated Dependency:");
            System.out.println("  Group ID: " + record.updatedDependency().dependencyGroupId());
            System.out.println("  Artifact ID: " + record.updatedDependency().dependencyArtifactId());
            System.out.println("  Previous Version: " + record.updatedDependency().previousVersion());
            System.out.println("  New Version: " + record.updatedDependency().newVersion());
        }

        if (record.breakingUpdateReproductionCommand() != null) {
            System.out.println("Reproduction Command: " + record.breakingUpdateReproductionCommand());
        }

        System.out.println();
    }

    private Map<String, BreakingUpdateRecord> buildRecordIndex(List<BreakingUpdateRecord> records) {
        Map<String, BreakingUpdateRecord> index = new HashMap<>();
        for (BreakingUpdateRecord record : records) {
            if (record.breakingCommit() != null) {
                index.put(record.breakingCommit(), record);
            }
        }
        return index;
    }

    private List<ClassificationSummary> buildDatasetOnlySummaries(List<BreakingUpdateRecord> records) {
        return records.stream()
                .map(record -> new ClassificationSummary(
                        record.project(),
                        record.breakingCommit(),
                        record.failureCategory(),
                        null,
                        null,
                        null,
                        null,
                        null)) // Added null for attempts
                .toList();
    }

    private void writeClassificationSummary(Path targetJson,
            ClassificationSummary summary,
            Map<String, BreakingUpdateRecord> recordByCommit,
            Path outputDir,
            Map<String, ReportEntry> existingResults,
            boolean filterEnabled,
            boolean shouldClean) {
        // Thread-safe JSON writing: synchronize on lock to prevent concurrent writes
        synchronized (jsonWriteLock) {
            try {
                // Always ensure parent directory exists (never create the JSON file as a directory)
                if (targetJson.getParent() != null) {
                    Files.createDirectories(targetJson.getParent());
                }
                
                // Safety check: if targetJson exists as a directory (should never happen), remove it
                // breaking-updates-results.json is ALWAYS a file, never a directory
                if (Files.exists(targetJson) && Files.isDirectory(targetJson)) {
                    log.error("CRITICAL: breaking-updates-results.json exists as a directory at {}. This should never happen. Removing directory.", targetJson);
                    try {
                        FileSystemUtils.deleteDirectory(targetJson);
                        log.info("Removed incorrect directory at {} to create JSON file", targetJson);
                    } catch (IOException e) {
                        log.error("Failed to remove directory at {}: {}", targetJson, e.getMessage(), e);
                        System.err.println("Error: Could not remove directory to create JSON file: " + targetJson);
                        return; // Cannot proceed if we can't remove the directory
                    }
                }
                
                ObjectMapper mapper = new ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT);
                
                Map<String, ReportEntry> currentReportEntries = new LinkedHashMap<>();
                if (filterEnabled && existingResults != null) {
                    currentReportEntries.putAll(existingResults);
                } else if (Files.exists(targetJson) && Files.isRegularFile(targetJson)) {
                    try {
                        currentReportEntries = mapper.readValue(
                                targetJson.toFile(),
                                mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class,
                                        ReportEntry.class));
                    } catch (MismatchedInputException e) {
                        // Handle legacy array format
                        ReportEntry[] legacyEntries = mapper.readValue(targetJson.toFile(), ReportEntry[].class);
                        for (ReportEntry entry : legacyEntries) {
                            if (entry.breakingCommit() != null) {
                                currentReportEntries.put(entry.breakingCommit(), entry);
                            }
                        }
                    }
                }

            String key = summary.breakingCommit();
            if (key == null) {
                // Fallback to entry's commit if summary doesn't have it (though summary usually
                // should)
                ReportEntry tempEntry = buildReportEntry(summary);
                key = tempEntry.breakingCommit();
            }

                if (key != null) {
                    List<se.kth.models.Attempt> attempts = writePerCommitReport(targetJson.getParent(), outputDir, key,
                            summary, recordByCommit.get(key), shouldClean);

                // Always write to JSON, even if no attempts were generated (to record failures)
                // The attempts contain the failureCategory derived from analyzing the log after
                // building with transformed files
                if (attempts != null && !attempts.isEmpty()) {
                    summary = new ClassificationSummary(
                            summary.project(),
                            summary.breakingCommit(),
                            summary.datasetCategory(),
                            summary.inferredCategory(),
                            summary.logFile(),
                            summary.classifierReport(),
                            summary.dockerImage(),
                            attempts);

                    // Write to JSON after attempts are processed
                    ReportEntry entry = buildReportEntry(summary);
                    currentReportEntries.put(key, entry);
                    mapper.writeValue(targetJson.toFile(), currentReportEntries);
                    // Keep existingResults in sync so subsequent commits in this same run
                    // are not lost when writeClassificationSummary is called again
                    if (existingResults != null) {
                        existingResults.put(key, entry);
                    }
                    log.info("Classification summary written to {} (after {} attempts)", targetJson, attempts.size());
                } else {
                    // No attempts generated - create a failure attempt to record the failure
                    // Determine failure category based on available information
                    se.kth.models.FailureCategory failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;
                    String failureReason = "NO_ATTEMPTS_GENERATED";
                    
                    // Try to infer failure type from summary
                    if (summary.dockerImage() == null) {
                        failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;
                        failureReason = "NO_DOCKER_IMAGE";
                    } else if (summary.inferredCategory() != null) {
                        // Try to use the inferred category if available
                        try {
                            failureCategory = se.kth.models.FailureCategory.valueOf(summary.inferredCategory());
                        } catch (IllegalArgumentException e) {
                            // Keep UNKNOWN_FAILURE if category doesn't match
                        }
                    }
                    
                    String logFileParent = summary.logFile() != null ? new java.io.File(summary.logFile()).getParent() : "";
                    List<se.kth.models.Attempt> failureAttempts = java.util.List.of(
                            new se.kth.models.Attempt(1, failureCategory, logFileParent, false));
                    
                    ClassificationSummary failureSummary = new ClassificationSummary(
                            summary.project(),
                            summary.breakingCommit(),
                            summary.datasetCategory(),
                            failureCategory.toString(),
                            summary.logFile(),
                            summary.classifierReport(),
                            summary.dockerImage(),
                            failureAttempts);
                    
                    ReportEntry entry = buildReportEntry(failureSummary);
                    currentReportEntries.put(key, entry);
                    mapper.writeValue(targetJson.toFile(), currentReportEntries);
                    // Keep existingResults in sync so subsequent commits in this same run
                    // are not lost when writeClassificationSummary is called again
                    if (existingResults != null) {
                        existingResults.put(key, entry);
                    }
                    log.warn("Classification summary written to {} with failure attempt (reason: {})", 
                            targetJson, failureReason);
                }
                } else {
                    // If no key, still write the summary (for backward compatibility)
                    ReportEntry entry = buildReportEntry(summary);
                    if (entry.breakingCommit() != null) {
                        currentReportEntries.put(entry.breakingCommit(), entry);
                        // Keep existingResults in sync so subsequent commits in this same run
                        // are not lost when writeClassificationSummary is called again
                        if (existingResults != null) {
                            existingResults.put(entry.breakingCommit(), entry);
                        }
                    }
                    mapper.writeValue(targetJson.toFile(), currentReportEntries);
                    log.info("Classification summary written to {}", targetJson);
                }
                if (!verbose) {
                    System.out.printf(Locale.ROOT, "Classification summary written to %s%n", targetJson);
                }
            } catch (IOException e) {
                log.error("Failed to write classification summary to {}", targetJson, e);
                System.err.println("Error: Could not write classification summary JSON: " + e.getMessage());
            }
        }
    }

    private ReportEntry buildReportEntry(ClassificationSummary summary) {
        String commit = summary.breakingCommit() != null ? summary.breakingCommit() : "unknown";
        String originalCategory = summary.datasetCategory();
        String inferred = summary.inferredCategory();
        
        // Extract processId from attempts (all attempts should have the same processId)
        String processId = null;
        String fullProcessId = null;
        BranchesInfo branches = null;
        
        // If attempts are available, use them to build the ReportEntry
        if (summary.attempts() != null && !summary.attempts().isEmpty()) {
            // Extract processId from first attempt (all should have the same)
            se.kth.models.Attempt firstAttempt = summary.attempts().get(0);
            processId = firstAttempt.getProcessId();
            
            // Build branches info based on processId and pipeline type
            branches = buildBranchesInfo(summary, processId);
            
            List<AttemptReport> attemptReports = summary.attempts().stream()
                    .map(attempt -> new AttemptReport(
                            attempt.getAttemptCount(),
                            attempt.getProcessId(),
                            attempt.getFailureCategory().toString(),
                            0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0,
                            parentOrSelf(attempt.getLogFileParent()),
                            attempt.isSuccessful(),
                            attempt.getContainerId()))
                    .collect(Collectors.toList());
            return new ReportEntry(commit, originalCategory, processId, fullProcessId, branches, attemptReports);
        } else {
            // Fallback to single attempt if no detailed attempts are provided
            AttemptReport attempt = new AttemptReport(
                    1,
                    null,
                    inferred != null ? inferred : originalCategory,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    summary.logFile() != null ? parentOrSelf(summary.logFile()) : "",
                    inferred != null && "BUILD_SUCCESS".equalsIgnoreCase(inferred),
                    null);
            return new ReportEntry(commit, originalCategory, processId, fullProcessId, branches, java.util.List.of(attempt));
        }
    }

    /**
     * Builds branches information based on the processId and pipeline type.
     */
    private BranchesInfo buildBranchesInfo(ClassificationSummary summary, String processId) {
        if (processId == null || processId.isBlank()) {
            return null;
        }
        
        String mainBranch = "main";
        String agentBranch = null;
        String repairBranch = null;
        List<String> attemptBranches = new ArrayList<>();
        
        // Determine pipeline type from attempts or environment
        String pipelineType = determinePipelineType();
        
        if ("agent".equals(pipelineType)) {
            // Agent pipeline: main -> agent-{ruleGen}-{processId}
            // We need to determine the rule generator name
            String ruleGenerator = envConfig.get("RULE_GENERATOR").orElse("spoon");
            agentBranch = String.format("agent-%s-%s", ruleGenerator, processId);
        } else {
            // Model pipeline: main -> repair/{category}-{processId} -> attempt_{N}-{processId}
            String category = summary.datasetCategory() != null 
                    ? summary.datasetCategory().toLowerCase() 
                    : "unknown";
            repairBranch = String.format("repair/%s-%s", category, processId);
            
            // Build attempt branch names from attempts
            if (summary.attempts() != null) {
                for (se.kth.models.Attempt attempt : summary.attempts()) {
                    attemptBranches.add(String.format("attempt_%d-%s", attempt.getAttemptCount(), processId));
                }
            }
        }
        
        return new BranchesInfo(mainBranch, agentBranch, repairBranch, attemptBranches);
    }

    /**
     * Loads existing results from breaking-updates-results.json if it exists.
     * 
     * @param jsonOutputPath the path to breaking-updates-results.json
     * @return Map of breakingCommit -> ReportEntry, or empty map if file doesn't exist
     */
    private Map<String, ReportEntry> loadExistingResults(Path jsonOutputPath) {
        Map<String, ReportEntry> existingResults = new LinkedHashMap<>();
        
        if (jsonOutputPath == null || !Files.exists(jsonOutputPath)) {
            // File doesn't exist - this is normal for first execution, no action needed
            log.debug("No existing results file found at {} - first execution", jsonOutputPath);
            return existingResults;
        }
        
        // Check if path is a directory instead of a file
        if (Files.isDirectory(jsonOutputPath)) {
            log.warn("Expected JSON file but found directory at {}. Skipping load of existing results.", jsonOutputPath);
            return existingResults;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
            
            // Try to read as Map<String, ReportEntry>
            try {
                existingResults = mapper.readValue(
                        jsonOutputPath.toFile(),
                        mapper.getTypeFactory().constructMapType(
                                LinkedHashMap.class, 
                                String.class, 
                                ReportEntry.class));
                
                log.debug("Loaded {} existing results from {}", existingResults.size(), jsonOutputPath);
                
            } catch (MismatchedInputException e) {
                // Handle legacy array format
                try {
                    ReportEntry[] legacyEntries = mapper.readValue(
                            jsonOutputPath.toFile(), 
                            ReportEntry[].class);
                    for (ReportEntry entry : legacyEntries) {
                        if (entry.breakingCommit() != null) {
                            existingResults.put(entry.breakingCommit(), entry);
                        }
                    }
                    log.debug("Loaded {} existing results from legacy format", existingResults.size());
                } catch (IOException legacyEx) {
                    log.warn("Failed to read existing results (legacy format): {}", legacyEx.getMessage());
                }
            }
            
        } catch (IOException e) {
            log.warn("Failed to load existing results from {}: {}", jsonOutputPath, e.getMessage());
        }
        
        return existingResults;
    }

    /**
     * Filters BreakingUpdateRecords to exclude already processed commits.
     * 
     * @param records the list of all records to process
     * @param processedCommits set of commit hashes that have already been processed
     * @return filtered list containing only unprocessed commits
     */
    private List<BreakingUpdateRecord> filterRecords(
            List<BreakingUpdateRecord> records, 
            Set<String> processedCommits) {
        
        if (processedCommits == null || processedCommits.isEmpty()) {
            return records; // No filtering needed
        }
        
        List<BreakingUpdateRecord> filtered = records.stream()
                .filter(record -> {
                    String commit = record.breakingCommit();
                    boolean alreadyProcessed = processedCommits.contains(commit);
                    
                    if (alreadyProcessed) {
                        log.debug("Skipping already processed commit: {}", commit);
                    }
                    
                    return !alreadyProcessed;
                })
                .collect(Collectors.toList());
        
        return filtered;
    }

    private String parentOrSelf(String path) {
        try {
            Path resolved = Paths.get(path);
            Path parent = resolved.getParent();
            return parent != null ? parent.toString() : resolved.toString();
        } catch (Exception e) {
            return path;
        }
    }

    private List<se.kth.models.Attempt> writePerCommitReport(Path reportBase,
            Path outputBaseDir,
            String commit,
            ClassificationSummary summary,
            BreakingUpdateRecord record,
            boolean shouldClean) {
        if (reportBase == null || commit == null) {
            return null;
        }
        try {
            Path commitDir = reportBase.resolve(commit);
            Files.createDirectories(commitDir);

            // STEP 1: Find and analyze initial log from project
            // The initial log is inside the project directory: {commit}.log or
            // {breakingCommit}.log
            Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit());
            Path projectDir = ProjectPaths.resolveProjectDir(commitOutputDir, record.project());
            Path initialLogFile = null;
            Path initialClassifierReport = null;

            if (Files.exists(projectDir)) {
                // Find the initial log file in the project
                initialLogFile = com.example.core.pipeline.ProjectLogLocator.findLogFile(
                        projectDir, record.project(), record.breakingCommit());

                if (initialLogFile != null && Files.exists(initialLogFile)) {
                    log.info("Found initial log file: {}", initialLogFile);

                    // Analyze initial log with breaking-classifier
                    try {
                        initialClassifierReport = commitDir.resolve("breaking-classifier-report.json");
                        github.chains.breakingclassifier.BreakingClassifierApp classifierApp = new github.chains.breakingclassifier.BreakingClassifierApp();
                        github.chains.breakingclassifier.BreakingReport breakingReport = classifierApp
                                .analyzeLog(initialLogFile, initialClassifierReport);

                        if (breakingReport != null) {
                            log.info("Initial log analyzed. Category: {}, Files with errors: {}",
                                    breakingReport.failureCategory(), breakingReport.errorsByFile().size());
                        }
                    } catch (Exception e) {
                        log.error("Failed to analyze initial log: {}", e.getMessage(), e);
                        initialClassifierReport = null;
                    }
                } else {
                    log.warn("Initial log file not found in project directory: {}", projectDir);
                }
            }

            // STEP 2: Initial Analysis (before repair loop)
            // This generates breaking-changes.json only
            // Note: change-impact.json is generated per attempt (not here) because
            // errors/files can change in each attempt
            if (changeImpactReportService != null && initialClassifierReport != null) {
                try {
                    // This generates:
                    // - breaking-changes.json (once per commit, based on japicmp)
                    // Note: change-impact.json is generated within each attempt in the repair
                    // pipeline
                    // Note: prompts and transformed files are generated within each attempt based
                    // on previous attempt's log
                    changeImpactReportService.copyAndGenerate(record, summary, outputBaseDir, commitDir,
                            initialClassifierReport);
                } catch (Throwable analysisError) {
                    String projectName = record != null ? record.project() : "unknown";
                    String message = analysisError.getMessage() != null
                            ? analysisError.getMessage()
                            : analysisError.getClass().getSimpleName();
                    log.error("Change-impact analysis failed for {} (project: {}): {}", commit, projectName, message);
                    System.err.printf("Change-impact failed for %s (%s): %s%n", commit, projectName, message);
                }
            } else if (initialClassifierReport != null) {
                // Just copy the classifier report if change-impact service is not available
                Path classifierTarget = commitDir.resolve("breaking-classifier-report.json");
                if (!Files.exists(classifierTarget)) {
                    Files.copy(initialClassifierReport, classifierTarget, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // STEP 3: Repair Loop - processes transformed files for each attempt
            // This happens AFTER initial analysis is done
            // Each attempt: uses log from previous attempt → generates prompts → generates
            // transformed files → builds → analyzes
            List<se.kth.models.Attempt> attempts = null;
            se.kth.models.FailureCategory failureCategory = null;
            String failureReason = null;
            
            // Check if repair pipeline was already executed (e.g., in parallel processing)
            // If summary already has attempts, skip repair pipeline execution to avoid duplicate runs
            if (summary.attempts() != null && !summary.attempts().isEmpty()) {
                log.info("Repair pipeline already executed (attempts found in summary). Skipping duplicate execution for commit: {}", commit);
                attempts = summary.attempts();
            } else if (summary.dockerImage() != null && repairPipeline != null) {
                try {
                    attempts = repairPipeline.runRepairLoop(commitOutputDir, record.project(), summary.dockerImage(),
                            record, commitDir, summary, outputBaseDir, initialLogFile);

                    // Update summary with attempts from repair loop
                    // The failureCategory of each attempt comes from analyzing the log AFTER
                    // building with transformed files
                    // This includes TRANSFORMATION_FAILURE attempts when Spoon transformation fails
                    if (attempts != null && !attempts.isEmpty()) {
                        List<se.kth.models.Attempt> updatedAttempts = new ArrayList<>();
                        if (summary.attempts() != null) {
                            updatedAttempts.addAll(summary.attempts());
                        }
                        updatedAttempts.addAll(attempts);

                        // Get the last attempt's category and log file
                        se.kth.models.Attempt lastAttempt = attempts.get(attempts.size() - 1);
                        String finalCategory = lastAttempt.getFailureCategory().toString();
                        String finalLogFile = null;
                        Path lastLogFile = commitDir.resolve("attempt_" + lastAttempt.getAttemptCount() + "_build.log");
                        if (Files.exists(lastLogFile)) {
                            finalLogFile = lastLogFile.toString();
                        }

                        // Update summary with all attempts (including TRANSFORMATION_FAILURE)
                        summary = new ClassificationSummary(
                                summary.project(),
                                summary.breakingCommit(),
                                summary.datasetCategory(),
                                finalCategory,
                                finalLogFile != null ? finalLogFile : summary.logFile(),
                                summary.classifierReport(),
                                summary.dockerImage(),
                                updatedAttempts);

                        // Log transformation failure if it occurred
                        if (lastAttempt.getFailureCategory() == se.kth.models.FailureCategory.TRANSFORMATION_FAILURE) {
                            log.warn(
                                    "Spoon transformation failed for commit {}. Stopping repair loop for this commit. Continuing with next commit.",
                                    record.breakingCommit());
                        }
                    } else {
                        // No attempts returned - pipeline failed silently (should not happen with updated AgentRepairPipeline)
                        failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;
                        failureReason = "PIPELINE_RETURNED_EMPTY";
                        log.warn("Repair pipeline returned no attempts for commit: {}", commit);
                    }
                } catch (com.example.core.pipeline.ProviderLimitException e) {
                    // Provider limit (OpenRouter 402/403 / OpenCode free-tier): propagate so
                    // writeClassificationSummary can write the JSON entry before stopping the pipeline
                    log.warn("Provider limit reached for commit {}: {}", record.breakingCommit(), e.getMessage());
                    throw e;
                } catch (Exception e) {
                    // Any other exception in repair loop - determine failure type from exception
                    log.error("Error in repair loop for commit {}: {}", record.breakingCommit(), e.getMessage(), e);
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                    if (errorMsg.contains("docker") || errorMsg.contains("container") ||
                        errorMsg.contains("cannot connect") || errorMsg.contains("timeout") ||
                        errorMsg.contains("connection refused") || errorMsg.contains("network")) {
                        failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;  // Container/Docker failure
                        failureReason = "CONTAINER_ERROR";
                    } else if (errorMsg.contains("agent") || errorMsg.contains("transformation") ||
                               errorMsg.contains("spoon")) {
                        failureCategory = se.kth.models.FailureCategory.TRANSFORMATION_FAILURE;  // Agent error
                        failureReason = "AGENT_ERROR";
                    } else {
                        failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;
                        failureReason = "PIPELINE_EXCEPTION";
                    }
                    // Don't update summary - keep original state
                }
            } else {
                // No docker image or repair pipeline available - cannot run repair pipeline
                if (summary.dockerImage() == null) {
                    failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;
                    failureReason = "NO_DOCKER_IMAGE";
                    log.warn("Docker image not available for repair pipeline (commit: {})", commit);
                } else if (repairPipeline == null) {
                    failureCategory = se.kth.models.FailureCategory.UNKNOWN_FAILURE;
                    failureReason = "NO_REPAIR_PIPELINE";
                    log.warn("Repair pipeline not available for commit: {}", commit);
                }
            }
            
            // If no attempts were generated but we have a failure category, create a failure attempt
            if ((attempts == null || attempts.isEmpty()) && failureCategory != null) {
                String logFileParent = commitDir != null ? commitDir.toString() : (summary.logFile() != null ? new java.io.File(summary.logFile()).getParent() : "");
                attempts = java.util.List.of(new se.kth.models.Attempt(
                        1,
                        failureCategory,
                        logFileParent,
                        false));
                log.info("Created failure attempt for commit {} with category: {} (reason: {})", 
                        commit, failureCategory, failureReason != null ? failureReason : "UNKNOWN");
            }

            return attempts;
        } catch (IOException e) {
            log.warn("Failed to write per-commit report for {}: {}", commit, e.getMessage());
            return null;
        }
    }

    private void cleanReportsAndJson(List<BreakingUpdateRecord> records, Path jsonFile) {
        if (jsonFile == null) {
            return;
        }

        // Check if path is a directory instead of a file
        if (Files.exists(jsonFile) && Files.isDirectory(jsonFile)) {
            log.warn("Expected JSON file but found directory at {}. Skipping JSON cleanup.", jsonFile);
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            Set<String> commitsToRemove = records.stream()
                    .map(BreakingUpdateRecord::breakingCommit)
                    .collect(Collectors.toSet());

            if (!Files.exists(jsonFile)) {
                // File doesn't exist - this is normal for first execution, no cleanup needed
                return;
            }

            // Try reading as map keyed by breakingCommit (new format)
            try {
                Map<String, ReportEntry> existing = mapper.readValue(
                        jsonFile.toFile(),
                        mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, ReportEntry.class));

                boolean changed = false;
                for (String commit : commitsToRemove) {
                    if (existing.remove(commit) != null) {
                        changed = true;
                    }
                }

                if (changed) {
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    mapper.writeValue(jsonFile.toFile(), existing);
                    log.info("Removed {} entries from JSON report (map format)", commitsToRemove.size());
                }

            } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
                // Not a map: try legacy array of ReportEntry
                try {
                    ReportEntry[] legacyEntries = mapper.readValue(jsonFile.toFile(), ReportEntry[].class);
                    List<ReportEntry> filtered = java.util.Arrays.stream(legacyEntries)
                            .filter(entry -> entry == null || entry.breakingCommit() == null
                                    || !commitsToRemove.contains(entry.breakingCommit()))
                            .collect(Collectors.toList());

                    if (filtered.size() != legacyEntries.length) {
                        mapper.enable(SerializationFeature.INDENT_OUTPUT);
                        mapper.writeValue(jsonFile.toFile(), filtered);
                        log.info("Removed {} entries from JSON report (array format)",
                                legacyEntries.length - filtered.size());
                    }
                } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e2) {
                    // Not an array: try legacy list of ClassificationSummary
                    try {
                        List<ClassificationSummary> summaries = mapper.readValue(jsonFile.toFile(),
                                new TypeReference<List<ClassificationSummary>>() {
                                });
                        List<ClassificationSummary> filteredSummaries = summaries.stream()
                                .filter(s -> s == null || s.breakingCommit() == null
                                        || !commitsToRemove.contains(s.breakingCommit()))
                                .collect(Collectors.toList());

                        if (filteredSummaries.size() != summaries.size()) {
                            mapper.enable(SerializationFeature.INDENT_OUTPUT);
                            mapper.writeValue(jsonFile.toFile(), filteredSummaries);
                            log.info("Removed {} entries from JSON report (legacy summaries)",
                                    summaries.size() - filteredSummaries.size());
                        }
                    } catch (Exception ex) {
                        // File not in any expected format or invalid; nothing to remove
                        log.debug("JSON report is not in a recognized format or is invalid: {}", jsonFile);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to process JSON report as legacy ReportEntry array: {}", ex.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to clean JSON report: {}", e.getMessage());
            }

            // Remove report directories under the JSON parent folder
            Path reportBase = jsonFile.getParent();
            if (reportBase != null) {
                for (String commit : commitsToRemove) {
                    Path commitDir = reportBase.resolve(commit);
                    if (Files.exists(commitDir)) {
                        try {
                            FileSystemUtils.deleteDirectory(commitDir);
                            log.info("Removed report directory: {}", commitDir);
                        } catch (IOException e) {
                            log.warn("Failed to remove report directory: {}", commitDir, e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to clean reports and JSON", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReportEntry(
            String breakingCommit,
            String originalFailureCategory,
            String processId,
            String fullProcessId,
            BranchesInfo branches,
            java.util.List<AttemptReport> attempts) {
    }

    private record BranchesInfo(
            String main,
            String agent,
            String repair,
            java.util.List<String> attempts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AttemptReport(
            int index,
            String processId,
            String failureCategory,
            int prefixFiles,
            int postfixFiles,
            int fixedFiles,
            int unfixedFiles,
            int newFiles,
            int prefixErrors,
            int postfixErrors,
            int fixedErrors,
            int unfixedErrors,
            int newErrors,
            String outputFolder,
            boolean successful,
            String containerId) {
    }

    /**
     * Determines which pipeline type to use based on CLI argument, environment
     * variable, or .env file.
     * Order of precedence:
     * 1. CLI argument (-p/--pipeline)
     * 2. System environment variable (REPAIR_PIPELINE)
     * 3. .env file (REPAIR_PIPELINE)
     * 4. Default: "model"
     *
     * @return the pipeline type to use ("model" or "agent")
     */
    private String determinePipelineType() {
        // First check CLI argument (highest priority)
        String pipelineType = options.getPipelineType();
        if (pipelineType != null && !pipelineType.trim().isEmpty()) {
            String normalized = pipelineType.trim().toLowerCase();
            if (normalized.equals("model") || normalized.equals("agent")) {
                return normalized;
            } else {
                log.warn("Invalid pipeline type '{}'. Valid options are 'model' or 'agent'. Using default 'model'.",
                        pipelineType);
                return "model";
            }
        }

        // Then check system environment variable (second priority)
        String systemEnvPipelineType = envConfig.get("REPAIR_PIPELINE").orElse(null);
        if (systemEnvPipelineType != null && !systemEnvPipelineType.trim().isEmpty()) {
            String normalized = systemEnvPipelineType.trim().toLowerCase();
            if (normalized.equals("model") || normalized.equals("agent")) {
                log.info("Using pipeline type from system environment variable: {}", normalized);
                return normalized;
            } else {
                log.warn(
                        "Invalid pipeline type in system environment variable REPAIR_PIPELINE '{}'. Valid options are 'model' or 'agent'. Checking .env file...",
                        systemEnvPipelineType);
            }
        }

        // Then check .env file (third priority)
        String envFilePipelineType = envConfig.get("REPAIR_PIPELINE").orElse(null);
        if (envFilePipelineType != null && !envFilePipelineType.trim().isEmpty()) {
            String normalized = envFilePipelineType.trim().toLowerCase();
            if (normalized.equals("model") || normalized.equals("agent")) {
                log.info("Using pipeline type from .env file: {}", normalized);
                return normalized;
            } else {
                log.warn(
                        "Invalid pipeline type in .env file REPAIR_PIPELINE '{}'. Valid options are 'model' or 'agent'. Using default 'model'.",
                        envFilePipelineType);
                return "model";
            }
        }

        // Default to model pipeline
        return "model";
    }

    /**
     * Creates the appropriate repair pipeline instance based on the type.
     *
     * @param pipelineType the type of pipeline to create ("model" or "agent")
     * @return an instance of RepairPipeline
     * @throws IllegalArgumentException if pipelineType is not recognized
     */
    private RepairPipeline createRepairPipeline(String pipelineType) {
        switch (pipelineType.toLowerCase()) {
            case "model":
                return new ModelRepairPipeline(gitWorkflowService, dockerBuild, envConfig, this.verbose);
            case "agent":
                return new AgentRepairPipeline(dockerBuild, envConfig, this.verbose);
            default:
                log.warn("Unknown pipeline type '{}'. Defaulting to model pipeline.", pipelineType);
                return new ModelRepairPipeline(gitWorkflowService, dockerBuild, envConfig, this.verbose);
        }
    }

    /**
     * Configures external library loggers (JGit, docker-java, Spoon) to respect the
     * verbose flag.
     * When verbose is false, DEBUG logs from these libraries are suppressed.
     */
    private void configureExternalLibraryLogging(boolean verbose) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Configure JGit logger
            ch.qos.logback.classic.Logger jgitLogger = loggerContext.getLogger("org.eclipse.jgit");
            if (verbose) {
                jgitLogger.setLevel(Level.DEBUG);
            } else {
                jgitLogger.setLevel(Level.INFO);
            }

            log.debug("Configured external library loggers - verbose: {}", verbose);
        } catch (Exception e) {
            // If logback is not available or there's an error, just log a debug message
            log.debug("Could not configure external library logging level: {}", e.getMessage());
        }
    }
}
