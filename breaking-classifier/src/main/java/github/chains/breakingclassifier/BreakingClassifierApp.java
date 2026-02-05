package github.chains.breakingclassifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "breaking-classifier",
        mixinStandardHelpOptions = true,
        description = "Extracts compiler errors from a Maven build log.")
public class BreakingClassifierApp implements Callable<Integer> {

    private final MavenErrorExtractor extractor;
    private final ObjectMapper objectMapper;

    @Parameters(index = "0", paramLabel = "REPORT_PATH",
            description = "Path to the Maven build log file.")
    private Path logPath;

    @Option(names = {"-j", "--json-output"}, paramLabel = "OUTPUT_JSON",
            description = "Write the extracted errors as JSON to the specified file.")
    private Path jsonOutput;

    public BreakingClassifierApp() {
        this(new MavenErrorExtractor(), new ObjectMapper().findAndRegisterModules());
    }

    public BreakingClassifierApp(MavenErrorExtractor extractor, ObjectMapper objectMapper) {
        this.extractor = extractor;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BreakingClassifierApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        BreakingReport report = analyzeLog(logPath, jsonOutput);
        if (report == null) {
            System.err.println("Unable to analyze log file: " + logPath);
            return 1;
        }

        if (report.errorsByFile().isEmpty()) {
            System.out.println("No compiler errors detected in " + logPath);
            return 0;
        }

        System.out.printf("Failure log: %s%n", report.originalFailurePath());
        System.out.printf("Failure category: %s%n", report.failureCategory());

        for (FileErrorGroup fileErrors : report.errorsByFile()) {
            System.out.printf("%s [%s]%n", fileErrors.filePath(), report.failureCategory());
            for (ErrorDetail error : fileErrors.errors()) {
                String column = error.columnNumber() != null ? ":" + error.columnNumber() : "";
                System.out.printf("  %d%s: %s%n", error.lineNumber(), column, error.message());
                for (String detail : error.details()) {
                    System.out.println("    " + detail);
                }
            }
        }
        return 0;
    }

    /**
     * Analyzes the provided log file and optionally writes the JSON report.
     *
     * @param logFile    the Maven build log to analyze
     * @param targetJson optional JSON output path; may be {@code null}
     * @return the aggregated {@link BreakingReport}, or {@code null} if analysis failed
     */
    public BreakingReport analyzeLog(Path logFile, Path targetJson) throws IOException {
        List<BreakingError> errors = extractor.extract(logFile);
        BreakingReport report = ErrorReportAggregator.aggregate(logFile, errors);
        writeJsonIfRequested(report, targetJson);
        return report;
    }

    /**
     * Convenience overload that analyzes a log file without writing JSON output.
     *
     * @param logFile the Maven build log to analyze
     * @return the aggregated report, or {@code null} if analysis failed
     */
    public BreakingReport analyzeLog(Path logFile) throws IOException {
        return analyzeLog(logFile, null);
    }

    private void writeJsonIfRequested(BreakingReport report, Path targetJson) throws IOException {
        if (targetJson == null) {
            return;
        }
        if (targetJson.getParent() != null) {
            Files.createDirectories(targetJson.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetJson.toFile(), report);
        System.out.println("JSON report written to " + targetJson);
    }
}

