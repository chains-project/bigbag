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
import java.util.List;

/**
 * Coordinates the Spoon line analysis with japicmp results to build the change-impact report.
 */
public class ChangeImpactAnalyzer {

    public ChangeImpactReport analyze(ChangeImpactRequest request) {
        Path normalizedProject = normalizeProject(request.projectPath());
        Path resolvedSource = resolveSourceFile(normalizedProject, request.sourceFile());

        LineConstructAnalyzer analyzer = LineConstructAnalyzer.initialize(normalizedProject);
        List<ConstructUsage> usages = analyzer.analyze(resolvedSource, request.lineNumber());

        ComparisonReport comparisonReport = JapicmpDiffTool.generateComparisonReport(
                request.oldJar(),
                request.newJar()
        );

        ApiChangeIndex index = ApiChangeIndex.fromReport(comparisonReport);
        List<ConstructImpact> constructImpacts = new ApiChangeMatcher(index).match(usages);

        return new ChangeImpactReport(
                normalizedProject.toString(),
                resolvedSource.toString(),
                request.lineNumber(),
                request.oldJar().toString(),
                request.newJar().toString(),
                Instant.now().toString(),
                constructImpacts
        );
    }

    private Path normalizeProject(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Project path is not a directory: " + normalized);
        }
        return normalized;
    }

    private Path resolveSourceFile(Path projectRoot, Path sourceFile) {
        Path candidate = sourceFile.isAbsolute() ? sourceFile : projectRoot.resolve(sourceFile);
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Source file not found: " + normalized);
        }
        return normalized;
    }
}

