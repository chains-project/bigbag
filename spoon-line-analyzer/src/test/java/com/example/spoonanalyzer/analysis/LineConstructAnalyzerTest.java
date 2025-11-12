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
                    assertThat(usage.getCodeLine()).isEqualTo("import java.util.List;");
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
                    assertThat(usage.getCodeLine()).isNotNull();
                    assertThat(usage.getCodeLine().trim()).isEqualTo("List<String> result = helper.provide();");
                });

        assertThat(usages)
                .as("field read of helper")
                .anySatisfy(usage -> {
                    assertThat(usage.getConstructType()).isEqualTo(ConstructType.FIELD_ACCESS);
                    assertThat(usage.getSignature()).isEqualTo("com.example.sample.App::helper");
                    assertThat(usage.getDependencyInfo().getOrigin()).isEqualTo(DependencyOrigin.PROJECT_SOURCE);
                    assertThat(usage.getCodeLine()).isNotNull();
                    assertThat(usage.getCodeLine().trim()).isEqualTo("List<String> result = helper.provide();");
                });
    }

    @Test
    void extractsSignatureForUnresolvedImport() {
        List<ConstructUsage> usages = analyzer.analyze(unresolvedImportSource, 3);

        assertThat(usages)
                .filteredOn(usage -> usage.getConstructType() == ConstructType.IMPORT)
                .extracting(ConstructUsage::getSignature)
                .contains("com.legacy.missing.LegacyTool");

        assertThat(usages)
                .filteredOn(usage -> usage.getConstructType() == ConstructType.IMPORT)
                .extracting(ConstructUsage::getCodeLine)
                .contains("import com.legacy.missing.LegacyTool;");
    }

    @Test
    void readsCodeLineFromSpoonSourceCode() {
        // Test that code lines are read from Spoon's compilation unit, not file system
        // Line 3: import statement (has constructs)
        List<ConstructUsage> usages3 = analyzer.analyze(sampleSource, 3);
        assertThat(usages3)
                .as("Import usage should have correct code line from Spoon")
                .isNotEmpty()
                .anySatisfy(usage -> {
                    assertThat(usage.getCodeLine())
                            .as("Code line should be the import statement from Spoon's source")
                            .isNotNull()
                            .isEqualTo("import java.util.List;");
                });

        // Line 7: field initialization with constructor call (has constructs)
        List<ConstructUsage> usages7 = analyzer.analyze(sampleSource, 7);
        assertThat(usages7)
                .as("Constructor call line should be captured from Spoon")
                .isNotEmpty()
                .allSatisfy(usage -> {
                    assertThat(usage.getCodeLine())
                            .as("Code line should match the field initialization line")
                            .isNotNull()
                            .isEqualTo("    private final Helper helper = new Helper();");
                });

        // Line 10: method call with field access (has constructs)
        List<ConstructUsage> usages10 = analyzer.analyze(sampleSource, 10);
        assertThat(usages10)
                .as("Method invocation line should be captured correctly from Spoon")
                .isNotEmpty()
                .allSatisfy(usage -> {
                    assertThat(usage.getCodeLine())
                            .as("Code line should match the method call line from Spoon's source")
                            .isNotNull()
                            .isEqualTo("        List<String> result = helper.provide();");
                });
    }

    @Test
    void handlesDifferentLineNumbersCorrectly() {
        // Test various line numbers to ensure Spoon source code reading works correctly
        Path helperSource = projectPath.resolve("src/main/java/com/example/sample/Helper.java");

        // Line 3: import statement (has constructs)
        List<ConstructUsage> usages3 = analyzer.analyze(helperSource, 3);
        assertThat(usages3)
                .as("Import line should be readable from Spoon")
                .isNotEmpty()
                .anySatisfy(usage -> {
                    assertThat(usage.getCodeLine())
                            .as("Code line should be captured from Spoon's source")
                            .isNotNull()
                            .isEqualTo("import java.util.Collections;");
                });

        // Line 4: import statement (has constructs)
        List<ConstructUsage> usages4 = analyzer.analyze(helperSource, 4);
        assertThat(usages4)
                .as("Import line should be readable from Spoon")
                .isNotEmpty()
                .anySatisfy(usage -> {
                    assertThat(usage.getCodeLine())
                            .as("Code line should be captured from Spoon's source")
                            .isNotNull()
                            .isEqualTo("import java.util.List;");
                });

        // Line 9: method body with method call (has constructs)
        List<ConstructUsage> usages9 = analyzer.analyze(helperSource, 9);
        assertThat(usages9)
                .as("Method call line should be readable from Spoon")
                .isNotEmpty()
                .anySatisfy(usage -> {
                    assertThat(usage.getCodeLine())
                            .as("Code line should be captured from Spoon")
                            .isNotNull()
                            .contains("singletonList");
                });
    }

    @Test
    void codeLinePreservesOriginalFormatting() {
        // Test that code lines preserve the original formatting from Spoon's source
        List<ConstructUsage> usages = analyzer.analyze(sampleSource, 10);

        assertThat(usages)
                .as("Code line should preserve indentation and formatting")
                .isNotEmpty()
                .allSatisfy(usage -> {
                    String codeLine = usage.getCodeLine();
                    assertThat(codeLine)
                            .as("Code line should not be null")
                            .isNotNull();
                    // Should preserve the indentation (spaces before the code)
                    assertThat(codeLine)
                            .as("Code line should start with indentation")
                            .startsWith("        "); // 8 spaces for method body
                    // Should contain the actual code
                    assertThat(codeLine.trim())
                            .as("Trimmed code line should contain the method call")
                            .isEqualTo("List<String> result = helper.provide();");
                });
    }

    @Test
    void codeLineIsConsistentAcrossAllUsagesOnSameLine() {
        // Test that all usages on the same line have the same code line content
        List<ConstructUsage> usages = analyzer.analyze(sampleSource, 10);

        assertThat(usages)
                .as("Should have multiple usages on line 10")
                .hasSizeGreaterThan(1);

        // All usages on the same line should have identical code line content
        String firstCodeLine = usages.get(0).getCodeLine();
        assertThat(usages)
                .as("All usages on the same line should have the same code line")
                .extracting(ConstructUsage::getCodeLine)
                .containsOnly(firstCodeLine);
    }
}

