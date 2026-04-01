package github.chains.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.FailureCategory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parser for command line arguments for the pipeline application.
 */
public class PipelineArgumentsParser {

    private static final Logger log = LoggerFactory.getLogger(PipelineArgumentsParser.class);

    /**
     * Parses command line arguments into a PipelineArguments record.
     *
     * @param args command line arguments
     * @return parsed PipelineArguments
     * @throws IllegalArgumentException if required arguments are missing or invalid
     */
    public static PipelineArguments parse(String[] args) {
        Path inputDir = null;
        Path projectsPath = null;
        Path outputPath = null;
        int maxAttempts = PipelineConfig.DEFAULT_MAX_ATTEMPTS;
        int threadPoolSize = PipelineConfig.DEFAULT_THREAD_POOL_SIZE;
        boolean forceReextraction = false;
        FailureCategory filterCategory = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> {
                    if (i + 1 < args.length) {
                        inputDir = Paths.get(args[++i]);
                    } else {
                        throw new IllegalArgumentException("--input requires a directory path");
                    }
                }
                case "--projects", "-p" -> {
                    if (i + 1 < args.length) {
                        projectsPath = Paths.get(args[++i]);
                    } else {
                        throw new IllegalArgumentException("--projects requires a directory path");
                    }
                }
                case "--output", "-o" -> {
                    if (i + 1 < args.length) {
                        outputPath = Paths.get(args[++i]);
                    } else {
                        throw new IllegalArgumentException("--output requires a directory path");
                    }
                }
                case "--attempts", "-a" -> {
                    if (i + 1 < args.length) {
                        try {
                            maxAttempts = Integer.parseInt(args[++i]);
                            if (maxAttempts < 1) {
                                throw new IllegalArgumentException("--attempts must be >= 1");
                            }
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("--attempts requires a valid integer", e);
                        }
                    } else {
                        throw new IllegalArgumentException("--attempts requires a number");
                    }
                }
                case "--threads", "-t" -> {
                    if (i + 1 < args.length) {
                        try {
                            threadPoolSize = Integer.parseInt(args[++i]);
                            if (threadPoolSize < 1) {
                                throw new IllegalArgumentException("--threads must be >= 1");
                            }
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("--threads requires a valid integer", e);
                        }
                    } else {
                        throw new IllegalArgumentException("--threads requires a number");
                    }
                }
                case "--force", "-f" -> forceReextraction = true;
                case "--category", "-c" -> {
                    if (i + 1 < args.length) {
                        try {
                            filterCategory = FailureCategory.valueOf(args[++i].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Invalid failure category: " + args[i], e);
                        }
                    } else {
                        throw new IllegalArgumentException("--category requires a category name");
                    }
                }
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> {
                    if (!args[i].startsWith("-")) {
                        // If no flag, assume it's the input directory (for convenience)
                        if (inputDir == null) {
                            inputDir = Paths.get(args[i]);
                        } else {
                            log.warn("Unknown argument: {}", args[i]);
                        }
                    } else {
                        log.warn("Unknown option: {}", args[i]);
                    }
                }
            }
        }

        if (inputDir == null) {
            throw new IllegalArgumentException("Input directory is required. Use --input or -i to specify.");
        }

        return new PipelineArguments(
                inputDir,
                projectsPath,
                outputPath,
                maxAttempts,
                threadPoolSize,
                forceReextraction,
                filterCategory
        );
    }

    public static void printUsage() {
        System.out.println("Usage: BreakingUpdatePipelineApp [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input DIR          Input directory containing BreakingUpdateRecord JSON files (required)");
        System.out.println("  -p, --projects DIR       Directory where extracted projects will be saved (default: ./projects)");
        System.out.println("  -o, --output DIR         Directory where results will be saved (default: ./output)");
        System.out.println("  -a, --attempts N         Number of build attempts per project (default: 1)");
        System.out.println("  -t, --threads N          Thread pool size for parallel processing (default: 4)");
        System.out.println("  -c, --category CATEGORY Filter by failure category (e.g., COMPILATION_FAILURE)");
        System.out.println("  -f, --force             Force re-extraction even if project already exists");
        System.out.println("  -h, --help              Show this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  BreakingUpdatePipelineApp -i ./breaking-updates -p ./projects -o ./results -a 3 -t 8");
    }
}

