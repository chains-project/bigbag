package com.example.spoonanalyzer;

import com.example.spoonanalyzer.analysis.LineConstructAnalyzer;
import com.example.spoonanalyzer.model.ConstructUsage;
import com.example.spoonanalyzer.model.DependencyInfo;
import com.example.spoonanalyzer.model.DependencyOrigin;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "spoon-line-analyzer",
        description = "Analyze a Maven project with Spoon and list constructs used on a specific line",
        mixinStandardHelpOptions = true
)
public class App implements Callable<Integer> {

    @CommandLine.Option(names = {"-p", "--project"}, required = true, description = "Path to the Maven project to analyze")
    private Path projectPath;

    @CommandLine.Option(names = {"-f", "--file"}, required = true, description = "Path to the source file to inspect (absolute or relative to the project root)")
    private Path sourceFile;

    @CommandLine.Option(names = {"-l", "--line"}, required = true, description = "Line number to analyze (1-based)")
    private int lineNumber;

    @CommandLine.Option(names = {"-d", "--dependency"}, description = "Optional dependency filter (match against coordinates or origin)")
    private String dependencyFilter;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @CommandLine.Option(
            names = {"-c", "--classpath"},
            description = "Additional classpath entry (JAR or directory). Repeat option for multiple entries.")
    private List<Path> extraClasspath = new ArrayList<>();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            LineConstructAnalyzer analyzer = LineConstructAnalyzer.initialize(projectPath, extraClasspath);
            List<ConstructUsage> usages = analyzer.analyze(sourceFile, lineNumber);

            usages.stream()
                    .filter(this::matchesDependencyFilter)
                    .forEach(this::printUsage);

            if (usages.isEmpty()) {
                System.out.println("No constructs found on the specified line.");
            }
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

    private boolean matchesDependencyFilter(ConstructUsage usage) {
        if (dependencyFilter == null || dependencyFilter.isBlank()) {
            return true;
        }
        DependencyInfo info = usage.getDependencyInfo();
        String filter = dependencyFilter.trim();
        if (info.getOrigin().name().equalsIgnoreCase(filter)) {
            return true;
        }
        if (info.getGroupId() != null && info.getGroupId().equalsIgnoreCase(filter)) {
            return true;
        }
        if (info.getArtifactId() != null && info.getArtifactId().equalsIgnoreCase(filter)) {
            return true;
        }
        return info.shortCoordinates().toLowerCase().contains(filter.toLowerCase());
    }

    private void printUsage(ConstructUsage usage) {
        System.out.printf("- %-18s %s%n", usage.getConstructType(), usage.getSignature());
        DependencyInfo info = usage.getDependencyInfo();
        System.out.printf("  Dependency: %s (%s)%n", info.shortCoordinates(), info.getOrigin());
        if (info.getOrigin() == DependencyOrigin.MAVEN_DEPENDENCY && info.getSourcePath() != null) {
            System.out.printf("  Artifact path: %s%n", info.getSourcePath());
        } else if (info.getOrigin() == DependencyOrigin.PROJECT_SOURCE && info.getSourcePath() != null) {
            System.out.printf("  Source path: %s%n", info.getSourcePath());
        }
    }
}

