package com.example.core.cli;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.report.JsonReportReader;
import com.example.core.service.BreakingUpdateExtractionService;
import com.example.core.service.ChangeImpactReportService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private ChangeImpactReportService changeImpactReportService;

    public MainCli() {
        this(EnvConfig.loadDefault());
    }

    public MainCli(EnvConfig envConfig) {
        this.envConfig = envConfig;
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
            if (this.changeImpactReportService == null) {
                this.changeImpactReportService = new ChangeImpactReportService(this.verbose, envConfig);
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
            BreakingUpdateExtractionService extractionService = new BreakingUpdateExtractionService(verbose);

            if (shouldExtract) {
                log.info("=== Starting Project Extraction ===");
                System.out.println("\n=== Extracting Projects from Docker Images ===");

                // If clean mode is enabled, remove all existing folders BEFORE processing
                if (shouldClean) {
                    System.out.println(
                            "Clean mode enabled: Removing existing {breakingCommit} folders before processing...");
                    extractionService.cleanExistingFolders(records, outputDir);
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
                        null))
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

            ReportEntry entry = buildReportEntry(summary);
            String key = summary.breakingCommit() != null ? summary.breakingCommit() : entry.breakingCommit();
            existing.put(key, entry);
            if (key != null) {
                writePerCommitReport(targetJson.getParent(), outputDir, key, summary, recordByCommit.get(key));
            }

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
        AttemptReport attempt = new AttemptReport(
                1,
                inferred != null ? inferred : originalCategory,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                summary.logFile() != null ? parentOrSelf(summary.logFile()) : "",
                inferred != null && "BUILD_SUCCESS".equalsIgnoreCase(inferred));
        return new ReportEntry(commit, originalCategory, java.util.List.of(attempt));
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

    private void writePerCommitReport(Path reportBase,
            Path outputBaseDir,
            String commit,
            ClassificationSummary summary,
            BreakingUpdateRecord record) {
        if (reportBase == null || commit == null) {
            return;
        }
        try {
            Path commitDir = reportBase.resolve(commit);
            Files.createDirectories(commitDir);

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
        } catch (IOException e) {
            log.warn("Failed to write per-commit report for {}: {}", commit, e.getMessage());
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
