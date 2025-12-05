package com.example.core.cli;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.prompt.PromptGenerationService;
import com.example.core.report.JsonReportReader;
import com.example.core.service.BreakingUpdateExtractionService;
import com.example.core.service.ChangeImpactReportService;
import com.example.core.service.GitWorkflowService;
import com.example.core.service.RepairLoopService;
import com.example.core.util.FileSystemUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.kth.DockerBuild;
import se.kth.models.Attempt;
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

    // Default configuration values
    private static final String DEFAULT_CATEGORY = "COMPILATION_FAILURE";
    private static final boolean DEFAULT_EXTRACT_PROJECTS = true;
    private static final boolean DEFAULT_EXTRACT_JARS_AND_CLASSIFY = true;
    private static final boolean DEFAULT_CLEAN_EXISTING = true;

    @CommandLine.Option(names = { "-i",
            "--input" }, description = "Input directory containing BreakingUpdateRecord JSON files")
    private String inputDirStr;

    @CommandLine.Option(names = { "-o",
            "--output" }, description = "Output directory where extracted projects will be saved")
    private String outputDirStr;

    @CommandLine.Option(names = { "-c",
            "--category" }, description = "Filter by failure category (COMPILATION_FAILURE, TEST_FAILURE, etc.)")
    private String category;

    @CommandLine.Option(names = { "-f",
            "--file" }, description = "Process only the specified JSON file (name without .json extension)")
    private String singleJsonFile;

    @CommandLine.Option(names = { "-e", "--extract" }, description = "Extract projects from Docker images")
    private Boolean extractProjects;

    @CommandLine.Option(names = { "--no-extract" }, description = "Do not extract projects from Docker images")
    private boolean noExtract;

    @CommandLine.Option(names = { "-k", "--classify" }, description = "Extract JARs and run breaking-classifier")
    private Boolean extractJarsAndClassify;

    @CommandLine.Option(names = { "--no-classify" }, description = "Do not extract JARs or run breaking-classifier")
    private boolean noClassify;

    @CommandLine.Option(names = {
            "--clean" }, description = "Remove existing {breakingCommit} folders before extraction")
    private Boolean cleanExisting;

    @CommandLine.Option(names = {
            "--no-clean" }, description = "Keep existing {breakingCommit} folders (skip if exists)")
    private boolean noClean;

    @CommandLine.Option(names = { "-v", "--verbose" }, description = "Show detailed information for each record")
    private boolean verbose;

    @CommandLine.Option(names = { "-j",
            "--json-output" }, description = "Path to store a JSON summary with dataset and inferred categories")
    private Path jsonOutput;

    private final EnvConfig envConfig;
    private final BreakingUpdateExtractionService extractionService;
    private final ChangeImpactReportService changeImpactReportService;
    private final PromptGenerationService promptGenerationService;
    private final GitWorkflowService gitWorkflowService;
    private final RepairLoopService repairLoopService;
    private final DockerBuild dockerBuild;

    public MainCli() {
        this.envConfig = EnvConfig.loadDefault();
        this.extractionService = new BreakingUpdateExtractionService(false);
        this.changeImpactReportService = new ChangeImpactReportService(false, envConfig);
        this.promptGenerationService = new PromptGenerationService(envConfig);
        this.gitWorkflowService = new GitWorkflowService();
        this.dockerBuild = new DockerBuild(false);
        this.repairLoopService = new RepairLoopService(gitWorkflowService, dockerBuild, envConfig, false);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MainCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // Merge CLI arguments with .env values
            String resolvedInputDir = inputDirStr != null ? inputDirStr : envConfig.require("INPUT_DIR");
            String resolvedOutputDir = outputDirStr != null ? outputDirStr : envConfig.require("OUTPUT_DIR");

            Path inputDir = Paths.get(resolvedInputDir);
            Path outputDir = Paths.get(resolvedOutputDir);

            boolean envExtract = envConfig.getBoolean("EXTRACT").orElse(DEFAULT_EXTRACT_PROJECTS);
            boolean envClassify = envConfig.getBoolean("CLASSIFY").orElse(DEFAULT_EXTRACT_JARS_AND_CLASSIFY);
            boolean envClean = envConfig.getBoolean("CLEAN").orElse(DEFAULT_CLEAN_EXISTING);
            boolean envVerbose = envConfig.getBoolean("VERBOSE").orElse(false);

            if (!this.verbose) {
                this.verbose = envVerbose;
            }

            boolean shouldExtract = (extractProjects != null ? extractProjects : envExtract) && !noExtract;
            boolean shouldClassify = (extractJarsAndClassify != null ? extractJarsAndClassify : envClassify)
                    && !noClassify;
            boolean shouldClean = (cleanExisting != null ? cleanExisting : envClean) && !noClean;

            if (singleJsonFile == null) {
                singleJsonFile = envConfig.get("FILE").orElse(null);
            }
            if (jsonOutput == null) {
                jsonOutput = envConfig.getPath("JSON_OUTPUT").orElse(null);
            }

            String modelName = envConfig.get("LLM_MODEL").orElse("default_model");

            // Adjust jsonOutput to be per-model
            if (jsonOutput != null) {
                Path parent = jsonOutput.getParent();
                if (parent == null) {
                    parent = Paths.get(".");
                }
                jsonOutput = parent.resolve(modelName).resolve(jsonOutput.getFileName());
            }

            String fileToProcess = singleJsonFile != null ? singleJsonFile
                    : envConfig.get("SPECIFIC_FILE").filter(s -> !s.isBlank()).orElse(null);

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

            // Filter by category if specified
            if (filterCategory != null) {
                final FailureCategory finalFilterCategory = filterCategory;
                int originalSize = records.size();
                records = records.stream()
                        .filter(record -> FailureCategoryUtils.matchesFailureCategory(record, finalFilterCategory))
                        .collect(Collectors.toList());
                log.info("Filtered to {} records matching category: {}", records.size(), filterCategory);
                System.out.println("\n=== Breaking Update Records ===");
                System.out.println("Total records found: " + originalSize);
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

                // If clean mode is enabled, remove all existing folders BEFORE processing
                if (shouldClean) {
                    System.out.println(
                            "Clean mode enabled: Removing existing {breakingCommit} folders before processing...");
                    extractionService.cleanExistingFolders(records, outputDir);

                    if (jsonOutput != null) {
                        cleanReportsAndJson(records, jsonOutput);
                    }
                }

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
                } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
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
                }
            }

            ReportEntry entry = buildReportEntry(summary);
            existing.put(key, entry);

            mapper.writeValue(targetJson.toFile(), existing);
            log.info("Classification summary written to {}", targetJson);
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

            // Run Repair Loop
            List<se.kth.models.Attempt> attempts = null;
            if (summary.dockerImage() != null) {
                try {
                    Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit()); // Renamed to avoid conflict
                                                                                           // with commitDir
                    attempts = repairLoopService.runRepairLoop(commitOutputDir, record.project(), summary.dockerImage(),
                            record);
                } catch (Exception e) {
                    log.error("Error in repair loop for {}", record.breakingCommit(), e);
                }
            }

            // Generate Prompts
            Path classifierSource = null;
            if (summary.classifierReport() != null) {
                Path possibleSource = Paths.get(summary.classifierReport());
                if (Files.exists(possibleSource)) {
                    classifierSource = possibleSource;
                }
            }

            if (changeImpactReportService != null) {
                try {
                    changeImpactReportService.copyAndGenerate(record, summary, outputBaseDir, commitDir,
                            classifierSource);
                } catch (Throwable analysisError) {
                    String projectName = record != null ? record.project() : "unknown";
                    String message = analysisError.getMessage() != null
                            ? analysisError.getMessage()
                            : analysisError.getClass().getSimpleName();
                    log.error("Change-impact analysis failed for {} (project: {}): {}", commit, projectName, message);
                    System.err.printf("Change-impact failed for %s (%s): %s%n", commit, projectName, message);
                }
            } else if (classifierSource != null) {
                Path classifierTarget = commitDir.resolve("breaking-classifier-report.json");
                Files.copy(classifierSource, classifierTarget, StandardCopyOption.REPLACE_EXISTING);
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

            // Read existing summaries
            List<ClassificationSummary> summaries = new ArrayList<>();
            if (Files.exists(jsonFile)) {
                try {
                    summaries = mapper.readValue(jsonFile.toFile(), new TypeReference<List<ClassificationSummary>>() {
                    });
                } catch (Exception e) {
                    // If file is empty or invalid, start fresh
                    summaries = new ArrayList<>();
                }
            }

            Set<String> commitsToRemove = records.stream()
                    .map(BreakingUpdateRecord::breakingCommit)
                    .collect(Collectors.toSet());

            // Remove entries
            List<ClassificationSummary> filteredSummaries = summaries.stream()
                    .filter(s -> !commitsToRemove.contains(s.breakingCommit()))
                    .collect(Collectors.toList());

            // Write back
            if (summaries.size() != filteredSummaries.size()) {
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(jsonFile.toFile(), filteredSummaries);
                log.info("Removed {} entries from JSON report", summaries.size() - filteredSummaries.size());
            }

            // Remove report directories
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

        } catch (IOException e) {
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
}
