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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // Filter: only process these commits
    public static final List<String> HASHES = Arrays.asList(
        "00a7cc31784ac4a9cc27d506a73ae589d6df36d6",
        "067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9",
        "07fad972bb884e9fa6143b4f870d08305811607d",
        "0abf7148300f40a1da0538ab060552bca4a2f1d8",
        "13fd75e233a5cb2771a6cb186c0decaed6d6545a",
        "165381d26b2c3d2278fde88c16f95807506451fe",
        "17f2bcaaba4805b218743f575919360c5aec5da4",
        "1820a966ae02ad8df44d0a0106cba65ceaf3aa95",
        "1c0972fc3d905b9f2a305a78f8a158a0b3fd8639",
        "1cc7071371953a7880c2c2c3a5a32c36af7f88f9",
        "1ef97ea6c5b6e34151fe6167001b69e003449f95",
        "249c3b394540fde4fcb72f66172af5e02b9c637e",
        "24d4a90ec1b375751e71f33d18949405c9529d77",
        "3572a1ecc0154c61e05505aed56055b9c5e539a6",
        "38c9915f0cfdf0c1a2b17c3c6f283c23a0aac0cf",
        "40feecdd9c649644668d7c84bb87b73a2b2723ca",
        "43b3a858b77ec27fc8946aba292001c3de465012",
        "43c824a24b09efd8b4b00449ce31cd121a6b23eb",
        "4a3efad6e00824e5814b9c8f571c9c98aad40281",
        "4aab2869639226035c999c282f31efba15648ea3",
        "54abbbde6a1233e1523a9b5f811ea100efb5dead",
        "5769bdad76925da568294cb8a40e7d4469699ac3",
        "5fcd0c3ad7727850c47602b17530dc355e5bd097",
        "61e96bfe3a32d6ef2e5d7912a518c78bd5474e74",
        "65200df71d5f6ab1c5502f74a5dc7bcbda459563",
        "6ad104c4fb9263ad1bb29e6b33618b8225efd92d",
        "6c53cd904bd66fc79af8687571e607c259226b81",
        "6c9a2ecf3bac1e0c7675e03b2828a71450d8ed45",
        "741f3b5e20a91b0e9305ae79261e3c5e64971c98",
        "7d97e1c7331f6722eb1d8192bf0a2686f5a33798",
        "7f7de81d28b68b091bef2e6f6ffd1836167be6ea",
        "832e0f184efdad0fcf15d14cb7af5e30239ff454",
        "867e69e208ff59d1f8baae7ed41d3e163a51bc65",
        "874ed893a4e46ea5182be2be054715967e58f08f",
        "90ffd2cd31edecf778d14d0015da9ceab7e53081",
        "9461431622cf39efe60cf1eb03a94083780c5720",
        "9717e34bcda74bd9ad94f6a52ddfd3fd179ea15b",
        "979d6237a50840cd925cc1a33c415ffbbbc42846",
        "9836e07e553e29f16ee35b5d7e4d0370e1789ecd",
        "9a8b6fc7847a0782ae4c48d0e4f7056507c0397d",
        "a26797cdeeecaa3b900ea1e0d5ec0cec66bf03ff",
        "a4c360001134c2e3a9f7fbde88a07a9fd767e78e",
        "a80dac86d1caa3958c45c036d93a7d9231d88fbf",
        "a9df7b2235224fcabefa1d62e8956911aa5bb825",
        "ab85440ce7321d895c7a9621224ce8059162a26a",
        "acc50dabec6796c091b84c1ada2ae4cbcab8b562",
        "ae0a0bd1311451e4a5a185a8d96405cfe3e049c5",
        "b554e03428f2ba877c33a0fece7f0f00fb38a5fa",
        "b5b64613a1a650a5784ff39386b4e00e05e5c21c",
        "bd3ce213e2771c6ef7817c80818807a757d4e94a",
        "c09896887acf0fe59320e01145a7034cd8d4e326",
        "c0f6ab75784dbc13ae8ff47298704c0756cf3a2c",
        "c311ee0a84b72b15ba64da3514181c2347912225",
        "c7c9590a206d4fb77dd05b9df391d888e6181667",
        "cbcafe129e143ef09401470e9d11de9758f298d0",
        "d38182a8a0fe1ec039aed97e103864fce717a0be",
        "d401e189fb6435110e3dc4ca1a94838f167e7ddf",
        "d54b56b91c11f21b97d4903143b04b7c1f10c255",
        "db02c6bcb989a5b0f08861c3344b532769530467",
        "dbdc7d2c4a28a8d65edcd0cdece91c0bc357b869",
        "dcc95f410847ab308db2f2a31ab13e32dc65c670",
        "f5bc873a4b68e87761a65064ebea9ad8c3fb085f",
        "f6659d758a437f8b676481fe70671a68a6ee1cde"
    );

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
    

    public MainCli() {
        this.envConfig = EnvConfig.loadDefault();
        // Services will be reinitialized with verbose flag in call() after determining verbose value
        this.extractionService = new BreakingUpdateExtractionService(false);
        this.changeImpactReportService = new ChangeImpactReportService(false, envConfig);
        this.promptGenerationService = new PromptGenerationService(envConfig);
        this.gitWorkflowService = new GitWorkflowService();
        this.dockerBuild = new DockerBuild(false, false);
        // Pipeline will be reinitialized in call() after determining verbose flag and pipeline type
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
            String resolvedInputDir = options.getInputDirStr() != null ? options.getInputDirStr() : envConfig.require("INPUT_DIR");
            String resolvedOutputDir = options.getOutputDirStr() != null ? options.getOutputDirStr() : envConfig.require("OUTPUT_DIR");

            Path inputDir = Paths.get(resolvedInputDir);
            Path outputDir = Paths.get(resolvedOutputDir);

            boolean envExtract = envConfig.getBoolean("EXTRACT").orElse(DEFAULT_EXTRACT_PROJECTS);
            boolean envClassify = envConfig.getBoolean("CLASSIFY").orElse(DEFAULT_EXTRACT_JARS_AND_CLASSIFY);
            boolean envClean = envConfig.getBoolean("CLEAN").orElse(DEFAULT_CLEAN_EXISTING);
            boolean envVerbose = envConfig.getBoolean("VERBOSE").orElse(false);

            // Set verbose from options or environment
            this.verbose = options.isVerbose() || envVerbose;

            // Reinitialize services with verbose flag to control logging (especially Docker image pull)
            this.extractionService = new BreakingUpdateExtractionService(this.verbose);
            this.dockerBuild = new DockerBuild(false, this.verbose);
            
            // Determine pipeline type from CLI argument or environment variable
            String selectedPipelineType = determinePipelineType();
            this.repairPipeline = createRepairPipeline(selectedPipelineType);
            log.info("Using repair pipeline: {}", selectedPipelineType);
            
            // Configure external library loggers based on verbose flag
            configureExternalLibraryLogging(this.verbose);

            boolean shouldExtract = (options.getExtractProjects() != null ? options.getExtractProjects() : envExtract) && !options.isNoExtract();
            boolean shouldClassify = (options.getExtractJarsAndClassify() != null ? options.getExtractJarsAndClassify() : envClassify)
                    && !options.isNoClassify();
            boolean shouldClean = (options.getCleanExisting() != null ? options.getCleanExisting() : envClean) && !options.isNoClean();

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
            // The JSON file will be generated at {jsonOutput}/{model}/breaking-updates-results.json
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
                // Generate path: {jsonOutputDir}/{model}/breaking-updates-results.json
                jsonOutput = jsonOutputDir.resolve(modelName).resolve("breaking-updates-results.json");
                log.info("JSON output will be written to: {}", jsonOutput);
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

            // Filter by commit hash - only process commits in HASHES list
            // This filter is only applied when no specific file is specified
            if (fileToProcess == null) {
                int originalSize = records.size();
                records = records.stream()
                        .filter(record -> record.breakingCommit() != null && HASHES.contains(record.breakingCommit()))
                        .collect(Collectors.toList());
                log.info("Filtered to {} records matching commit hashes (from {} total)", records.size(), originalSize);
                if (originalSize != records.size()) {
                    System.out.println("Filtered to " + records.size() + " records matching commit hashes (from " + originalSize + " total)");
                }
            } else {
                log.info("Processing specific file, skipping HASHES filter");
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

            Map<String, BreakingUpdateRecord> recordByCommit = buildRecordIndex(records);

            // If clean mode is enabled, remove all existing folders BEFORE processing
            // This should happen regardless of whether we're extracting or just processing
            if (shouldClean) {
                System.out.println(
                        "Clean mode enabled: Removing existing {breakingCommit} folders and reports before processing...");
                extractionService.cleanExistingFolders(records, outputDir);

                if (jsonOutput != null) {
                    cleanReportsAndJson(records, jsonOutput);
                }
            }

            Consumer<ClassificationSummary> summaryConsumer = null;
            if (jsonOutput != null) {
                final Path finalJsonOutput = jsonOutput;
                summaryConsumer = summary -> writeClassificationSummary(
                        finalJsonOutput,
                        summary,
                        recordByCommit,
                        outputDir);
            }

            // Extract or classify depending on requested actions
            List<ClassificationSummary> classificationSummaries = java.util.Collections.emptyList();
            // BreakingUpdateExtractionService extractionService = new
            // BreakingUpdateExtractionService(verbose); // Now initialized in constructor

            if (shouldExtract) {
                log.info("=== Starting Project Extraction ===");
                System.out.println("\n=== Extracting Projects from Docker Images ===");

                classificationSummaries = extractionService.extractProjectsFromDockerImages(
                        records,
                        outputDir,
                        shouldClassify,
                        shouldClean,
                        summaryConsumer);
            } else if (shouldClassify) {
                classificationSummaries = extractionService.classifyExistingProjects(records, outputDir,
                        summaryConsumer);
            }

            if (jsonOutput != null && (classificationSummaries == null || classificationSummaries.isEmpty())) {
                List<ClassificationSummary> datasetSummaries = buildDatasetOnlySummaries(records);
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
            Path outputDir) {
        try {
            if (targetJson.getParent() != null) {
                Files.createDirectories(targetJson.getParent());
            }
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
            Map<String, ReportEntry> existing = new LinkedHashMap<>();
            if (Files.exists(targetJson)) {
                try {
                    existing = mapper.readValue(
                            targetJson.toFile(),
                            mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class,
                                    ReportEntry.class));
                } catch (MismatchedInputException e) {
                    // Handle legacy array format
                    ReportEntry[] legacyEntries = mapper.readValue(targetJson.toFile(), ReportEntry[].class);
                    for (ReportEntry entry : legacyEntries) {
                        if (entry.breakingCommit() != null) {
                            existing.put(entry.breakingCommit(), entry);
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
                        summary, recordByCommit.get(key));
                
                // Only write to JSON if we have attempts (after attempt 1 is processed)
                // The attempts contain the failureCategory derived from analyzing the log after building with transformed files
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
                    
                    // Write to JSON only after attempts are processed
                    ReportEntry entry = buildReportEntry(summary);
                    existing.put(key, entry);
                    mapper.writeValue(targetJson.toFile(), existing);
                    log.info("Classification summary written to {} (after {} attempts)", targetJson, attempts.size());
                } else {
                    log.debug("Skipping JSON write for {} - no attempts processed yet", key);
                }
            } else {
                // If no key, still write the summary (for backward compatibility)
                ReportEntry entry = buildReportEntry(summary);
                if (entry.breakingCommit() != null) {
                    existing.put(entry.breakingCommit(), entry);
                }
                mapper.writeValue(targetJson.toFile(), existing);
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

    private ReportEntry buildReportEntry(ClassificationSummary summary) {
        String commit = summary.breakingCommit() != null ? summary.breakingCommit() : "unknown";
        String originalCategory = summary.datasetCategory();
        String inferred = summary.inferredCategory();
        // If attempts are available, use them to build the ReportEntry
        if (summary.attempts() != null && !summary.attempts().isEmpty()) {
            List<AttemptReport> attemptReports = summary.attempts().stream()
                    .map(attempt -> new AttemptReport(
                            attempt.getAttemptCount(),
                            attempt.getFailureCategory().toString(),
                            0, 0, 0, 0, 0, // Placeholder for file stats
                            0, 0, 0, 0, 0, // Placeholder for error stats
                            parentOrSelf(attempt.getLogFileParent()),
                            attempt.isSuccessful()))
                    .collect(Collectors.toList());
            return new ReportEntry(commit, originalCategory, attemptReports);
        } else {
            // Fallback to single attempt if no detailed attempts are provided
            AttemptReport attempt = new AttemptReport(
                    1,
                    inferred != null ? inferred : originalCategory,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    summary.logFile() != null ? parentOrSelf(summary.logFile()) : "",
                    inferred != null && "BUILD_SUCCESS".equalsIgnoreCase(inferred));
            return new ReportEntry(commit, originalCategory, java.util.List.of(attempt));
        }
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
            BreakingUpdateRecord record) {
        if (reportBase == null || commit == null) {
            return null;
        }
        try {
            Path commitDir = reportBase.resolve(commit);
            Files.createDirectories(commitDir);

            // STEP 1: Find and analyze initial log from project
            // The initial log is inside the project directory: {commit}.log or {breakingCommit}.log
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
                        github.chains.breakingclassifier.BreakingClassifierApp classifierApp = 
                                new github.chains.breakingclassifier.BreakingClassifierApp();
                        github.chains.breakingclassifier.BreakingReport breakingReport = 
                                classifierApp.analyzeLog(initialLogFile, initialClassifierReport);
                        
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
            // Note: change-impact.json is generated per attempt (not here) because errors/files can change in each attempt
            if (changeImpactReportService != null && initialClassifierReport != null) {
                try {
                    // This generates:
                    // - breaking-changes.json (once per commit, based on japicmp)
                    // Note: change-impact.json is generated within each attempt in the repair pipeline
                    // Note: prompts and transformed files are generated within each attempt based on previous attempt's log
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
            // Each attempt: uses log from previous attempt → generates prompts → generates transformed files → builds → analyzes
            List<se.kth.models.Attempt> attempts = null;
            if (summary.dockerImage() != null) {
                try {
                    attempts = repairPipeline.runRepairLoop(commitOutputDir, record.project(), summary.dockerImage(),
                            record, commitDir, summary, outputBaseDir, initialLogFile);
                    
                    // Update summary with attempts from repair loop
                    // The failureCategory of each attempt comes from analyzing the log AFTER building with transformed files
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
                                updatedAttempts
                        );
                        
                        // Log transformation failure if it occurred
                        if (lastAttempt.getFailureCategory() == se.kth.models.FailureCategory.TRANSFORMATION_FAILURE) {
                            log.warn("Spoon transformation failed for commit {}. Stopping repair loop for this commit. Continuing with next commit.", 
                                    record.breakingCommit());
                        }
                    }
                } catch (Exception e) {
                    // Any other exception in repair loop - log and continue with next commit
                    log.error("Error in repair loop for commit {}: {}", record.breakingCommit(), e.getMessage(), e);
                    // Don't update summary - keep original state
                }
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

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            Set<String> commitsToRemove = records.stream()
                    .map(BreakingUpdateRecord::breakingCommit)
                    .collect(Collectors.toSet());

            if (!Files.exists(jsonFile)) {
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
                        log.info("Removed {} entries from JSON report (array format)", legacyEntries.length - filtered.size());
                    }
                } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e2) {
                    // Not an array: try legacy list of ClassificationSummary
                    try {
                        List<ClassificationSummary> summaries = mapper.readValue(jsonFile.toFile(),
                                new TypeReference<List<ClassificationSummary>>() {
                                });
                        List<ClassificationSummary> filteredSummaries = summaries.stream()
                                .filter(s -> s == null || s.breakingCommit() == null || !commitsToRemove.contains(s.breakingCommit()))
                                .collect(Collectors.toList());

                        if (filteredSummaries.size() != summaries.size()) {
                            mapper.enable(SerializationFeature.INDENT_OUTPUT);
                            mapper.writeValue(jsonFile.toFile(), filteredSummaries);
                            log.info("Removed {} entries from JSON report (legacy summaries)", summaries.size() - filteredSummaries.size());
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
            java.util.List<AttemptReport> attempts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AttemptReport(
            int index,
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
            boolean successful) {
    }

    /**
     * Determines which pipeline type to use based on CLI argument, environment variable, or .env file.
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
                log.warn("Invalid pipeline type '{}'. Valid options are 'model' or 'agent'. Using default 'model'.", pipelineType);
                return "model";
            }
        }
        
        // Then check system environment variable (second priority)
        String systemEnvPipelineType = System.getenv("REPAIR_PIPELINE");
        if (systemEnvPipelineType != null && !systemEnvPipelineType.trim().isEmpty()) {
            String normalized = systemEnvPipelineType.trim().toLowerCase();
            if (normalized.equals("model") || normalized.equals("agent")) {
                log.info("Using pipeline type from system environment variable: {}", normalized);
                return normalized;
            } else {
                log.warn("Invalid pipeline type in system environment variable REPAIR_PIPELINE '{}'. Valid options are 'model' or 'agent'. Checking .env file...", systemEnvPipelineType);
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
                log.warn("Invalid pipeline type in .env file REPAIR_PIPELINE '{}'. Valid options are 'model' or 'agent'. Using default 'model'.", envFilePipelineType);
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
     * Configures external library loggers (JGit, docker-java, Spoon) to respect the verbose flag.
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
