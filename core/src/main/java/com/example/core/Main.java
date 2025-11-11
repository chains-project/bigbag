package com.example.core;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.report.JsonReportReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main class for processing breaking update records from JSON files.
 * Reads JSON files from a folder and extracts BreakingUpdateRecord information.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // Default configuration values - update these with your actual paths
    private static final String DEFAULT_INPUT_DIR = "/Users/frankreyesgarcia/Documents/WORK/PHD/Bump/bump/data/benchmark";
    private static final String DEFAULT_OUTPUT_DIR = "output";
    private static final String DEFAULT_CATEGORY = "COMPILATION_FAILURE"; // null = no filter, or "COMPILATION_FAILURE", "TEST_FAILURE", etc.

    public static void main(String[] args) {
        // Parse command line arguments
        String inputDirStr = DEFAULT_INPUT_DIR;
        String outputDirStr = DEFAULT_OUTPUT_DIR;
        boolean verbose = false;
        FailureCategory filterCategory = FailureCategoryUtils.parseFailureCategory(DEFAULT_CATEGORY);

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> {
                    if (i + 1 < args.length) {
                        inputDirStr = args[++i];
                    } else {
                        System.err.println("Error: --input requires a directory path");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--output", "-o" -> {
                    if (i + 1 < args.length) {
                        outputDirStr = args[++i];
                    } else {
                        System.err.println("Error: --output requires a directory path");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--category", "-c" -> {
                    if (i + 1 < args.length) {
                        try {
                            filterCategory = FailureCategoryUtils.parseFailureCategory(args[++i]);
                            if (filterCategory == FailureCategory.UNKNOWN_FAILURE && 
                                !args[i].equalsIgnoreCase("UNKNOWN_FAILURE")) {
                                System.err.println("Warning: Invalid category '" + args[i] + 
                                    "'. Use --help to see available categories.");
                            }
                        } catch (Exception e) {
                            System.err.println("Error: Invalid failure category: " + args[i]);
                            printUsage();
                            System.exit(1);
                            return;
                        }
                    } else {
                        System.err.println("Error: --category requires a category name");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--verbose", "-v" -> verbose = true;
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                    return;
                }
                default -> {
                    // If no flag, assume it's the input directory (for convenience)
                    if (inputDirStr.equals(DEFAULT_INPUT_DIR) && !args[i].startsWith("-")) {
                        inputDirStr = args[i];
                    } else {
                        System.err.println("Unknown option: " + args[i]);
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
            }
        }

        Path inputDir = Paths.get(inputDirStr);
        Path outputDir = Paths.get(outputDirStr);

        log.info("=== Breaking Update Record Extractor ===");
        log.info("Input directory: {}", inputDir);
        if (verbose) {
            log.info("Output directory: {}", outputDir);
        }
        if (filterCategory != null) {
            log.info("Filter category: {}", filterCategory);
            System.out.println("Filtering by category: " + filterCategory);
        }

        // Validate input directory
        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            log.error("Input directory does not exist or is not a directory: {}", inputDir);
            System.err.println("Error: Input directory does not exist: " + inputDir);
            printUsage();
            System.exit(1);
            return;
        }

        // Read and process JSON files
        try {
            JsonReportReader reader = new JsonReportReader();
            List<BreakingUpdateRecord> records = reader.readFromDirectory(inputDir);

            log.info("Found {} breaking update records", records.size());
            
            // Filter by category if specified
            if (filterCategory != null) {
                final FailureCategory finalFilterCategory = filterCategory; // Make final for lambda
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
                    // Simple output for non-verbose mode
                    System.out.println((i + 1) + ". " + record.project() + " - " + record.breakingCommit());
                }
            }
            
            if (!verbose && records.size() > 0) {
                System.out.println("\nUse --verbose to see detailed information for each record");
            }

            log.info("=== Processing Complete ===");
            System.out.println("\n=== Processing Complete ===");

        } catch (IOException e) {
            log.error("Error reading breaking update records from {}", inputDir, e);
            System.err.println("Error: Failed to read breaking update records: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Displays information from a BreakingUpdateRecord.
     *
     * @param index the record index
     * @param record the breaking update record
     */
    private static void displayRecordInfo(int index, BreakingUpdateRecord record) {
        System.out.println("--- Record #" + index + " ---");
        System.out.println("Project: " + (record.project() != null ? record.project() : "N/A"));
        System.out.println("Breaking Commit: " + (record.breakingCommit() != null ? record.breakingCommit() : "N/A"));
        System.out.println("URL: " + (record.url() != null ? record.url() : "N/A"));
        System.out.println("Failure Category: " + (record.failureCategory() != null ? record.failureCategory() : "N/A"));
        
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

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: Main [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input DIR     Input directory containing BreakingUpdateRecord JSON files");
        System.out.println("                      (default: " + DEFAULT_INPUT_DIR + ")");
        System.out.println("  -o, --output DIR    Output directory (default: " + DEFAULT_OUTPUT_DIR + ")");
        System.out.println("  -c, --category CAT  Filter by failure category (see categories below)");
        System.out.println("  -v, --verbose       Show detailed information for each record");
        System.out.println("  -h, --help          Show this help message");
        System.out.println();
        System.out.println("Available Failure Categories:");
        for (FailureCategory category : FailureCategory.values()) {
            System.out.println("  - " + category.name());
        }
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  Main -i ./breaking-updates");
        System.out.println("  Main --input ./my-data --category COMPILATION_FAILURE --verbose");
        System.out.println("  Main ./breaking-updates -c TEST_FAILURE  # Short form");
    }
}

