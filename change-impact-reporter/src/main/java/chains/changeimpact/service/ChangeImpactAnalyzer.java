package chains.changeimpact.service;

import chains.changeimpact.model.ChangeImpactReport;
import chains.changeimpact.model.ChangeImpactRequest;
import chains.changeimpact.model.ConstructImpact;
import com.example.japicmp.JapicmpDiffTool;
import com.example.japicmp.model.ComparisonReport;
import com.example.spoonanalyzer.analysis.LineConstructAnalyzer;
import com.example.spoonanalyzer.model.ConstructUsage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates the Spoon line analysis with japicmp results to build the change-impact report.
 */
public class ChangeImpactAnalyzer {

    public ChangeImpactReport analyze(ChangeImpactRequest request) {
        Session session = openSession(request.projectPath(), request.oldJar(), request.newJar());
        return session.analyze(request.sourceFile(), request.lineNumber());
    }

    /**
     * Creates a reusable analysis session for a project + dependency pair.
     * The session caches the Spoon model and japicmp data so callers can
     * analyze multiple files/lines without rebuilding everything.
     */
    public Session openSession(Path projectPath, Path oldJar, Path newJar) {
        return openSession(projectPath, oldJar, newJar, List.of());
    }

    /**
     * Creates a reusable analysis session for a project + dependency pair with additional classpath.
     * The session caches the Spoon model and japicmp data so callers can
     * analyze multiple files/lines without rebuilding everything.
     *
     * @param projectPath the project source directory
     * @param oldJar path to the old version JAR
     * @param newJar path to the new version JAR
     * @param additionalClasspath additional classpath entries (JARs or directories)
     */
    public Session openSession(Path projectPath, Path oldJar, Path newJar, List<Path> additionalClasspath) {
        Path normalizedProject = normalizeProject(projectPath);
        
        // Combine JARs with additional classpath
        List<Path> classpath = new ArrayList<>();
        classpath.add(oldJar);
        classpath.add(newJar);
        classpath.addAll(additionalClasspath);
        
        LineConstructAnalyzer analyzer = LineConstructAnalyzer.initialize(normalizedProject, classpath);
        ComparisonReport comparisonReport = JapicmpDiffTool.generateComparisonReport(oldJar, newJar);
        ApiChangeIndex index = ApiChangeIndex.fromReport(comparisonReport);
        ApiChangeMatcher matcher = new ApiChangeMatcher(index);
        return new Session(normalizedProject, oldJar, newJar, analyzer, matcher);
    }

    private static Path normalizeProject(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Project path is not a directory: " + normalized);
        }
        return normalized;
    }

    private static Path resolveSourceFile(Path projectRoot, Path sourceFile) {
        Path candidate = sourceFile.isAbsolute() ? sourceFile : projectRoot.resolve(sourceFile);
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Source file not found: " + normalized);
        }
        return normalized;
    }

    public static final class Session {
        private final Path projectRoot;
        private final Path oldJar;
        private final Path newJar;
        private final LineConstructAnalyzer analyzer;
        private final ApiChangeMatcher matcher;

        private Session(Path projectRoot,
                        Path oldJar,
                        Path newJar,
                        LineConstructAnalyzer analyzer,
                        ApiChangeMatcher matcher) {
            this.projectRoot = projectRoot;
            this.oldJar = oldJar.toAbsolutePath().normalize();
            this.newJar = newJar.toAbsolutePath().normalize();
            this.analyzer = analyzer;
            this.matcher = matcher;
        }

        public ChangeImpactReport analyze(Path sourceFile, int lineNumber) {
            Path resolvedSource = resolveSourceFile(projectRoot, sourceFile);
            List<ConstructUsage> usages = analyzer.analyze(resolvedSource, lineNumber);
            List<ConstructImpact> constructImpacts = matcher.match(usages);

            return new ChangeImpactReport(
                    projectRoot.toString(),
                    resolvedSource.toString(),
                    lineNumber,
                    oldJar.toString(),
                    newJar.toString(),
                    Instant.now().toString(),
                    constructImpacts
            );
        }
    }
}

