package chains.changeimpact.cli;

import chains.changeimpact.io.JsonReportWriter;
import chains.changeimpact.model.ChangeImpactReport;
import chains.changeimpact.model.ChangeImpactRequest;
import chains.changeimpact.service.ChangeImpactAnalyzer;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Picocli entry point that delegates analysis to {@link ChangeImpactAnalyzer} and writes the JSON report.
 */
@CommandLine.Command(
        name = "change-impact-reporter",
        mixinStandardHelpOptions = true,
        description = "Generate a JSON report with Spoon CT elements on a line and matching API changes"
)
public class ChangeImpactReporterApp implements Callable<Integer> {

    @CommandLine.Option(names = {"-p", "--project"}, required = true,
            description = "Path to the Maven project that contains the source file")
    private Path projectPath;

    @CommandLine.Option(names = {"-f", "--file"}, required = true,
            description = "Path to the source file to inspect (absolute or relative to the project root)")
    private Path sourceFile;

    @CommandLine.Option(names = {"-l", "--line"}, required = true,
            description = "1-based line number to analyze")
    private int lineNumber;

    @CommandLine.Option(names = {"--old-jar"}, required = true,
            description = "Path to the old/previous version of the dependency JAR")
    private Path oldJar;

    @CommandLine.Option(names = {"--new-jar"}, required = true,
            description = "Path to the new/current version of the dependency JAR")
    private Path newJar;

    @CommandLine.Option(names = {"-o", "--output"},
            description = "Output path for the generated JSON report (defaults to change-impact-report.json)")
    private Path outputPath;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Enable verbose error output")
    private boolean verbose;

    private final ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();
    private final JsonReportWriter reportWriter = new JsonReportWriter();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChangeImpactReporterApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            Path normalizedProject = projectPath.toAbsolutePath().normalize();
            Path resolvedOldJar = normalizeJarPath(oldJar, "old");
            Path resolvedNewJar = normalizeJarPath(newJar, "new");
            Path resolvedOutput = resolveOutputPath();

            ChangeImpactRequest request = new ChangeImpactRequest(
                    normalizedProject,
                    sourceFile,
                    lineNumber,
                    resolvedOldJar,
                    resolvedNewJar
            );

            ChangeImpactReport report = analyzer.analyze(request);
            reportWriter.write(report, resolvedOutput);

            System.out.printf(Locale.ROOT,
                    "Report generated at %s with %d constructs analysed.%n",
                    resolvedOutput,
                    report.constructs().size());
            return 0;
        } catch (Exception ex) {
            if (verbose) {
                ex.printStackTrace(System.err);
            } else {
                System.err.println("Error: " + ex.getMessage());
            }
            return 1;
        }
    }

    private Path normalizeJarPath(Path jar, String label) {
        Path normalized = jar.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("The " + label + " JAR does not exist: " + normalized);
        }
        return normalized;
    }

    private Path resolveOutputPath() {
        if (outputPath == null || outputPath.toString().isBlank()) {
            outputPath = Path.of("change-impact-report.json");
        }
        return outputPath.toAbsolutePath().normalize();
    }
}

