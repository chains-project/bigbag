package github.chains.japicmp;

import github.chains.japicmp.model.ComparisonReport;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command line tool that compares two JAR files using japicmp and emits an enriched JSON report.
 */
public final class JapicmpDiffTool {

    private JapicmpDiffTool() {
        // utility class
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: java -jar japicmp-diff-tool.jar <old-jar> <new-jar> [output-json]");
            System.exit(1);
        }

        Path oldJar = Path.of(args[0]).toAbsolutePath().normalize();
        Path newJar = Path.of(args[1]).toAbsolutePath().normalize();
        Path outputJson = args.length == 3
                ? Path.of(args[2]).toAbsolutePath().normalize()
                : Path.of("japicmp-report.json").toAbsolutePath();

        validateJarPath(oldJar, "old");
        validateJarPath(newJar, "new");

        try {
            ComparisonReport report = generateComparisonReport(oldJar, newJar);
            ComparisonReportWriter.write(report, outputJson);
            System.out.printf("Report written to %s with %d changed classes.%n",
                    outputJson, report.summary().changedClasses());
        } catch (Exception ex) {
            System.err.printf("Failed to generate report: %s%n", ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static void validateJarPath(Path jarPath, String label) {
        if (!Files.exists(jarPath)) {
            throw new IllegalArgumentException("Cannot find " + label + " JAR: " + jarPath);
        }
        if (!Files.isRegularFile(jarPath)) {
            throw new IllegalArgumentException("Path for " + label + " JAR is not a regular file: " + jarPath);
        }
    }

    public static ComparisonReport generateComparisonReport(Path oldJar, Path newJar) {
        return JapicmpReportGenerator.generate(oldJar, newJar);
    }
}

