package com.example.spoonanalyzer.analysis;

import com.example.spoonanalyzer.model.ConstructType;
import com.example.spoonanalyzer.model.ConstructUsage;
import com.example.spoonanalyzer.model.DependencyOrigin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineConstructAnalyzerTest {

    private static LineConstructAnalyzer analyzer;
    private static Path projectPath;
    private static Path sampleSource;
    private static Path unresolvedImportSource;

    @BeforeAll
    static void buildModel() {
        projectPath = Path.of("src/test/resources/sample-maven-project").toAbsolutePath().normalize();
        analyzer = LineConstructAnalyzer.initialize(projectPath);
        sampleSource = projectPath.resolve("src/main/java/com/example/sample/App.java");
        unresolvedImportSource = projectPath.resolve("src/main/java/com/example/sample/LegacyImport.java");
    }

    @Test
    void detectsImportUsageOnTargetLine() {
        List<ConstructUsage> usages = analyzer.analyze(sampleSource, 3);

        assertThat(usages)
                .as("import usages")
                .anySatisfy(usage -> {
                    assertThat(usage.getConstructType()).isEqualTo(ConstructType.IMPORT);
                    assertThat(usage.getSignature()).isEqualTo("java.util.List");
                    assertThat(usage.getDependencyInfo().getOrigin()).isEqualTo(DependencyOrigin.JDK);
                });
    }

    @Test
    void detectsMethodAndFieldUsageOnTargetLine() {
        List<ConstructUsage> usages = analyzer.analyze(sampleSource, 10);

        assertThat(usages)
                .as("method invocation on helper.provide()")
                .anySatisfy(usage -> {
                    assertThat(usage.getConstructType()).isEqualTo(ConstructType.METHOD_INVOCATION);
                    assertThat(usage.getSignature()).isEqualTo("com.example.sample.Helper#provide()");
                    assertThat(usage.getDependencyInfo().getOrigin()).isEqualTo(DependencyOrigin.PROJECT_SOURCE);
                });

        assertThat(usages)
                .as("field read of helper")
                .anySatisfy(usage -> {
                    assertThat(usage.getConstructType()).isEqualTo(ConstructType.FIELD_ACCESS);
                    assertThat(usage.getSignature()).isEqualTo("com.example.sample.App::helper");
                    assertThat(usage.getDependencyInfo().getOrigin()).isEqualTo(DependencyOrigin.PROJECT_SOURCE);
                });
    }

    @Test
    void extractsSignatureForUnresolvedImport() {
        List<ConstructUsage> usages = analyzer.analyze(unresolvedImportSource, 3);

        assertThat(usages)
                .filteredOn(usage -> usage.getConstructType() == ConstructType.IMPORT)
                .extracting(ConstructUsage::getSignature)
                .contains("com.legacy.missing.LegacyTool");
    }
}

