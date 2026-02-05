package com.example.core.bump;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.report.JsonReportReader;
import com.example.core.bump.model.VersionAnalysisReport;
import com.example.core.bump.model.VersionCombination;
import com.example.core.bump.VersionCombinationAnalyzer.AnalysisResult;
import com.example.core.bump.DependencyExtractor.ExtractionSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main class for analyzing version combinations in breaking update records.
 * Reads JSON files from a folder, optionally filters by failure category,
 * and analyzes unique combinations of previousVersion -> newVersion.
 */
public class BumpAnalyzerMain {
    
    private static final Logger log = LoggerFactory.getLogger(BumpAnalyzerMain.class);
    
    // Default configuration values
    private static final String DEFAULT_INPUT_DIR = "/Users/frankreyesgarcia/Documents/WORK/PHD/Bump/bump/data/benchmark";
    private static final String DEFAULT_OUTPUT_FILE = "version-combinations.json";
    
    public static void main(String[] args) {
        // Parse command line arguments
        String inputDirStr = DEFAULT_INPUT_DIR;
        String outputFileStr = DEFAULT_OUTPUT_FILE;
        boolean verbose = false;
        String singleJsonFile = "24d4a90ec1b375751e71f33d18949405c9529d77";
        FailureCategory filterCategory = FailureCategoryUtils.parseFailureCategory("COMPILATION_FAILURE");
        boolean extractDependencies = true;
        String dependenciesOutputDir = "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/dependencies/";
        String excludeCommitsFile = "analysis/java_version_incompatibility.txt";
        
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
                        outputFileStr = args[++i];
                    } else {
                        System.err.println("Error: --output requires a file path");
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
                case "--file", "-f" -> {
                    if (i + 1 < args.length) {
                        singleJsonFile = args[++i];
                    } else {
                        System.err.println("Error: --file requires a JSON filename or full path");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--extract-dependencies", "-e" -> {
                    extractDependencies = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        dependenciesOutputDir = args[++i];
                    }
                }
                case "--exclude-commits", "-x" -> {
                    if (i + 1 < args.length) {
                        excludeCommitsFile = args[++i];
                    } else {
                        System.err.println("Error: --exclude-commits requires a file path");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
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
        Path outputFile = Paths.get(outputFileStr);
        
        log.info("=== Version Combination Analyzer ===");
        log.info("Input directory: {}", inputDir);
        log.info("Output file: {}", outputFile);
        
        if (filterCategory != null) {
            log.info("Filter category: {}", filterCategory);
            System.out.println("Filtering by category: " + filterCategory);
        }
        
        if (singleJsonFile != null) {
            log.info("Processing single JSON file: {}", singleJsonFile);
            System.out.println("Processing single file: " + singleJsonFile);
        }
        
        // Read excluded commits if file is provided
        final Set<String> excludedCommits;
        if (excludeCommitsFile != null) {
            try {
                Path excludeFile = Paths.get(excludeCommitsFile);
                if (!Files.exists(excludeFile)) {
                    log.error("Exclude commits file does not exist: {}", excludeFile);
                    System.err.println("Error: Exclude commits file does not exist: " + excludeFile);
                    System.exit(1);
                    return;
                }
                excludedCommits = Files.readAllLines(excludeFile)
                    .stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toSet());
                log.info("Loaded {} commits to exclude from analysis", excludedCommits.size());
                System.out.println("Excluding " + excludedCommits.size() + " commits from analysis");
            } catch (IOException e) {
                log.error("Error reading exclude commits file: {}", excludeCommitsFile, e);
                System.err.println("Error: Failed to read exclude commits file: " + e.getMessage());
                System.exit(1);
                return;
            }
        } else {
            excludedCommits = new HashSet<>();
        }
        
        // Validate input directory (only if not processing a single file with full path)
        if (singleJsonFile == null && (!Files.exists(inputDir) || !Files.isDirectory(inputDir))) {
            log.error("Input directory does not exist or is not a directory: {}", inputDir);
            System.err.println("Error: Input directory does not exist: " + inputDir);
            printUsage();
            System.exit(1);
            return;
        }
        
        // Read and process JSON files
        try {
            JsonReportReader reader = new JsonReportReader();
            List<BreakingUpdateRecord> records;
            
            if (singleJsonFile != null) {
                // Read only the specified JSON file
                Path jsonFilePath;
                
                // Check if singleJsonFile is a full path (absolute or relative to current directory)
                Path potentialPath = Paths.get(singleJsonFile);
                if (Files.exists(potentialPath) && Files.isRegularFile(potentialPath)) {
                    // It's a full path to an existing file
                    jsonFilePath = potentialPath;
                } else {
                    // It's a filename in the input directory
                    // Validate input directory exists
                    if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
                        log.error("Input directory does not exist or is not a directory: {}", inputDir);
                        System.err.println("Error: Input directory does not exist: " + inputDir);
                        printUsage();
                        System.exit(1);
                        return;
                    }
                    // Try with .json extension first, then as-is
                    jsonFilePath = inputDir.resolve(singleJsonFile + ".json");
                    if (!Files.exists(jsonFilePath)) {
                        jsonFilePath = inputDir.resolve(singleJsonFile);
                    }
                }
                
                if (!Files.exists(jsonFilePath)) {
                    log.error("JSON file does not exist: {}", jsonFilePath);
                    System.err.println("Error: JSON file does not exist: " + jsonFilePath);
                    System.exit(1);
                    return;
                }
                
                log.info("Reading from file: {}", jsonFilePath.toAbsolutePath());
                records = reader.readFromFile(jsonFilePath);
            } else {
                // Read all JSON files from directory
                records = reader.readFromDirectory(inputDir);
            }
            
            log.info("Found {} breaking update records", records.size());
            System.out.println("\n=== Breaking Update Records ===");
            System.out.println("Total records found: " + records.size());
            
            // Filter out excluded commits
            if (!excludedCommits.isEmpty()) {
                int originalSize = records.size();
                records = records.stream()
                    .filter(record -> !excludedCommits.contains(record.breakingCommit()))
                    .collect(Collectors.toList());
                int excludedCount = originalSize - records.size();
                log.info("Excluded {} records based on commit exclusion list", excludedCount);
                System.out.println("Excluded " + excludedCount + " records (commits in exclusion list)");
                System.out.println("Remaining records: " + records.size());
            }
            
            // Analyze version combinations
            VersionCombinationAnalyzer analyzer = new VersionCombinationAnalyzer();
            AnalysisResult analysisResult = analyzer.analyze(records, filterCategory);
            final VersionAnalysisReport initialReport = analysisResult.report();
            VersionAnalysisReport report = initialReport;
            
            // Display summary
            System.out.println("\n=== Analysis Summary ===");
            System.out.println("Total records: " + report.totalRecords());
            System.out.println("Filtered records: " + report.filteredRecords());
            if (report.failureCategory() != null) {
                System.out.println("Failure category filter: " + report.failureCategory());
            }
            System.out.println("Unique version combinations: " + report.uniqueCombinations());
            
            // Display top combinations if verbose
            if (verbose && !report.combinations().isEmpty()) {
                System.out.println("\n=== Top 10 Version Combinations ===");
                int topN = Math.min(10, report.combinations().size());
                for (int i = 0; i < topN; i++) {
                    var combination = report.combinations().get(i);
                    System.out.println((i + 1) + ". " + 
                            combination.dependencyGroupId() + ":" + combination.dependencyArtifactId() + 
                            " " + combination.previousVersion() + 
                            " -> " + combination.newVersion() + 
                            " (count: " + combination.count() + 
                            ", projects: " + combination.projects().size() + ")");
                }
            }
            
            // Extract dependencies if requested
            if (extractDependencies) {
                System.out.println("\n=== Extracting Dependencies ===");
                Path dependenciesDir = Paths.get(dependenciesOutputDir);
                Files.createDirectories(dependenciesDir);
                
                DependencyExtractor extractor = new DependencyExtractor(dependenciesDir, verbose);
                
                // Create a map to track updated combinations
                Map<String, VersionCombination> updatedCombinationsMap = new HashMap<>();
                for (VersionCombination combination : report.combinations()) {
                    String combinationKey = combination.dependencyGroupId() + ":" + 
                                           combination.dependencyArtifactId() + ":" + 
                                           combination.previousVersion() + " -> " + combination.newVersion();
                    updatedCombinationsMap.put(combinationKey, combination);
                }
                
                // Set callback to update report after each combination
                extractor.setCombinationUpdateCallback((updatedCombination, diffLines) -> {
                    String combinationKey = updatedCombination.dependencyGroupId() + ":" + 
                                           updatedCombination.dependencyArtifactId() + ":" + 
                                           updatedCombination.previousVersion() + " -> " + updatedCombination.newVersion();
                    
                    // Update the combination in the map
                    updatedCombinationsMap.put(combinationKey, updatedCombination);
                    
                    // Create updated report with all combinations processed so far
                    List<VersionCombination> currentCombinations = new ArrayList<>();
                    for (VersionCombination original : initialReport.combinations()) {
                        String key = original.dependencyGroupId() + ":" + 
                                    original.dependencyArtifactId() + ":" + 
                                    original.previousVersion() + " -> " + original.newVersion();
                        VersionCombination updated = updatedCombinationsMap.get(key);
                        currentCombinations.add(updated != null ? updated : original);
                    }
                    
                    VersionAnalysisReport updatedReport = new VersionAnalysisReport(
                            initialReport.totalRecords(),
                            initialReport.filteredRecords(),
                            initialReport.failureCategory(),
                            initialReport.uniqueCombinations(),
                            currentCombinations
                    );
                    
                    // Write report immediately after each combination
                    try {
                        writeReportToJson(updatedReport, outputFile);
                        log.debug("Report updated after processing combination: {}", combinationKey);
                    } catch (IOException e) {
                        log.error("Failed to write report after combination {}: {}", combinationKey, e.getMessage());
                    }
                });
                
                ExtractionSummary extractionSummary = extractor.extractDependencies(
                        report.combinations(),
                        analysisResult.recordsByCombination()
                );
                
                System.out.println("\n=== Extraction Summary ===");
                System.out.println("Extracted: " + extractionSummary.extracted());
                System.out.println("Skipped (already exist): " + extractionSummary.skipped());
                System.out.println("Failed: " + extractionSummary.failed());
                System.out.println("Total: " + extractionSummary.total());
                System.out.println("Output directory: " + dependenciesDir.toAbsolutePath());
                
                // Final update of report with all combinations
                List<VersionCombination> finalCombinations = new ArrayList<>();
                for (VersionCombination original : report.combinations()) {
                    String key = original.dependencyGroupId() + ":" + 
                                original.dependencyArtifactId() + ":" + 
                                original.previousVersion() + " -> " + original.newVersion();
                    VersionCombination updated = updatedCombinationsMap.get(key);
                    finalCombinations.add(updated != null ? updated : original);
                }
                
                report = new VersionAnalysisReport(
                        report.totalRecords(),
                        report.filteredRecords(),
                        report.failureCategory(),
                        report.uniqueCombinations(),
                        finalCombinations
                );
            }
            
            // Write final report to JSON (in case extractDependencies was not called)
            writeReportToJson(report, outputFile);
            System.out.println("\n=== Report Generated ===");
            System.out.println("Output file: " + outputFile.toAbsolutePath());
            
            log.info("=== Analysis Complete ===");
            System.out.println("\n=== Analysis Complete ===");
            
        } catch (IOException e) {
            log.error("Error reading breaking update records from {}", inputDir, e);
            System.err.println("Error: Failed to read breaking update records: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            log.error("Unexpected error during analysis", e);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Writes the analysis report to a JSON file.
     *
     * @param report the analysis report
     * @param outputFile the output file path
     */
    private static void writeReportToJson(VersionAnalysisReport report, Path outputFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .registerModule(new JavaTimeModule());
        
        // Create parent directories if needed
        Path parent = outputFile.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        
        objectMapper.writeValue(outputFile.toFile(), report);
    }
    
    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: BumpAnalyzerMain [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input DIR     Input directory containing BreakingUpdateRecord JSON files");
        System.out.println("                      (default: " + DEFAULT_INPUT_DIR + ")");
        System.out.println("  -o, --output FILE  Output JSON file for version combinations report");
        System.out.println("                      (default: " + DEFAULT_OUTPUT_FILE + ")");
        System.out.println("  -c, --category CAT  Filter by failure category (see categories below)");
        System.out.println("  -f, --file FILE     Process only the specified JSON file");
        System.out.println("                      Can be: filename (without .json) in input directory, or full path");
        System.out.println("  -e, --extract-dependencies [DIR]  Extract JAR dependencies from Docker images");
        System.out.println("                      (optional DIR: output directory for dependencies)");
        System.out.println("                      (default: dependencies/)");
        System.out.println("  -x, --exclude-commits FILE  Exclude commits from analysis");
        System.out.println("                      File should contain one commit hash per line");
        System.out.println("  -v, --verbose       Show detailed information including top combinations");
        System.out.println("  -h, --help          Show this help message");
        System.out.println();
        System.out.println("Available Failure Categories:");
        for (FailureCategory category : FailureCategory.values()) {
            System.out.println("  - " + category.name());
        }
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  BumpAnalyzerMain -i ./breaking-updates -o ./version-combinations.json");
        System.out.println("  BumpAnalyzerMain --input ./data --category COMPILATION_FAILURE --verbose");
        System.out.println("  BumpAnalyzerMain -i ./data --file my-project -o ./output.json");
        System.out.println("  BumpAnalyzerMain --file /path/to/specific-file.json -o ./output.json");
        System.out.println("  BumpAnalyzerMain -i ./data -e ./deps --extract-dependencies");
        System.out.println("  BumpAnalyzerMain -i ./data -x ./excluded-commits.txt -o ./output.json");
        System.out.println();
        System.out.println("Output:");
        System.out.println("  Generates a JSON file with unique version combinations:");
        System.out.println("    - dependencyGroupId:artifactId");
        System.out.println("    - previousVersion -> newVersion");
        System.out.println("    - Count of occurrences");
        System.out.println("    - List of projects using this combination");
        System.out.println("    - List of breaking commits");
        System.out.println("    - List of failure categories");
        System.out.println();
        System.out.println("Dependencies Extraction:");
        System.out.println("  When --extract-dependencies is used, extracts JAR files from Docker images:");
        System.out.println("    - Organizes JARs by combination: {outputDir}/{groupId}/{artifactId}/{previousVersion}-{newVersion}/");
        System.out.println("    - Extracts both previous and new versions");
        System.out.println("    - Skips extraction if JARs already exist");
        System.out.println("    - Uses Docker images from breaking update records");
        System.out.println("    - Previous version from preCommitReproductionCommand");
        System.out.println("    - New version from breakingUpdateReproductionCommand");
    }
}

