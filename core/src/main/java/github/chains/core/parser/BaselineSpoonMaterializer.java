package github.chains.core.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Materializer for baseline_spoon prompt output.
 * <p>
 * The baseline_spoon prompt outputs ONLY executable Java code (a complete Spoon processor class)
 * wrapped in markdown code blocks (```java ... ```). This materializer:
 * <ul>
 *   <li>Extracts the Java code from markdown blocks</li>
 *   <li>Detects if it's a complete class extending AbstractProcessor</li>
 *   <li>Adapts the class to work with the correct input/output paths</li>
 *   <li>Generates the executable Java file</li>
 * </ul>
 */
public final class BaselineSpoonMaterializer implements Materializer {

    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile(
        "(?s)```(?:java)?\\s*(.*?)```",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    @Override
    public String id() {
        return "baseline_spoon";
    }

    @Override
    public Path materialize(Path promptOutputFile,
                           Path originalSourceFile,
                           Path commitReportDir,
                           String baseName) throws IOException {
        if (promptOutputFile == null || originalSourceFile == null || commitReportDir == null || baseName == null) {
            throw new IllegalArgumentException("promptOutputFile, originalSourceFile, commitReportDir and baseName must be non-null");
        }

        String content = Files.readString(promptOutputFile, StandardCharsets.UTF_8);
        
        // Extract Java code from markdown blocks
        String javaCode = extractJavaCodeFromMarkdown(content);
        
        if (javaCode.isBlank()) {
            throw new IOException("No Java code found in LLM output for baseline_spoon prompt");
        }

        Path targetDir = commitReportDir.resolve("spoon-rules");
        Files.createDirectories(targetDir);

        // 1) Save raw rules
        Path rawRules = targetDir.resolve(baseName + "_spoon_rules_raw.txt");
        Files.writeString(rawRules, javaCode, StandardCharsets.UTF_8);

        // 2) Check if it's a complete class
        boolean isCompleteClass = isCompleteSpoonClass(javaCode);
        boolean hasMainMethod = hasMainMethod(javaCode);
        
        Path driver;
        if (isCompleteClass && hasMainMethod) {
            // LLM provided a complete class with main - adapt it and save it
            String adaptedClass = adaptCompleteClass(javaCode, originalSourceFile, commitReportDir);
            String className = extractClassName(javaCode);
            driver = targetDir.resolve(className + ".java");
            Files.writeString(driver, adaptedClass, StandardCharsets.UTF_8);
        } else if (isCompleteClass) {
            // LLM provided a processor class without main - create wrapper with main
            String className = extractClassName(javaCode);
            String wrapperClass = buildWrapperWithMain(javaCode, className, originalSourceFile, commitReportDir);
            driver = targetDir.resolve(className + ".java");
            Files.writeString(driver, wrapperClass, StandardCharsets.UTF_8);
        } else {
            // If not a complete class, wrap it in a driver skeleton
            driver = targetDir.resolve("SpoonApplyRules.java");
            String driverSource = buildDriverSource(originalSourceFile, commitReportDir, rawRules, javaCode);
            Files.writeString(driver, driverSource, StandardCharsets.UTF_8);
        }
        
        return driver;
    }

    /**
     * Extracts Java code from markdown code blocks.
     */
    private String extractJavaCodeFromMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        Matcher matcher = MARKDOWN_CODE_BLOCK_PATTERN.matcher(content);
        StringBuilder allCode = new StringBuilder();

        while (matcher.find()) {
            String block = matcher.group(1).trim();
            if (!block.isEmpty()) {
                if (allCode.length() > 0) {
                    allCode.append("\n\n");
                }
                allCode.append(block);
            }
        }

        // If no markdown blocks found, check if content is raw Java code
        if (allCode.length() == 0) {
            String trimmed = content.trim();
            if (trimmed.startsWith("package ") || 
                trimmed.startsWith("import ") || 
                trimmed.startsWith("public class ")) {
                return trimmed;
            }
        }

        return allCode.toString();
    }

    /**
     * Detects if the Java code is a complete Spoon class.
     */
    private boolean isCompleteSpoonClass(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            return false;
        }
        
        String code = javaCode.toLowerCase();
        boolean hasPublicClass = code.contains("public class");
        boolean hasProcessor = code.contains("abstractprocessor") || code.contains("extends abstractprocessor");
        
        return hasPublicClass && hasProcessor;
    }

    /**
     * Detects if the Java code has a main method.
     */
    private boolean hasMainMethod(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            return false;
        }
        
        String code = javaCode.toLowerCase();
        return code.contains("public static void main");
    }

    /**
     * Extracts the class name from Java code.
     */
    private String extractClassName(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            return "SpoonApplyRules";
        }
        
        Pattern pattern = Pattern.compile("public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");
        Matcher matcher = pattern.matcher(javaCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "SpoonApplyRules";
    }

    /**
     * Adapts a complete Spoon class to work with the correct paths.
     * Uses the same logic as SpoonRulesMaterializer.adaptCompleteClass().
     */
    private String adaptCompleteClass(String javaCode, Path originalSourceFile, Path commitReportDir) {
        Path outputDir = commitReportDir.resolve("transformed");
        String outputDirPath = outputDir.toAbsolutePath().toString().replace("\\", "/");
        String inputPath = originalSourceFile.toAbsolutePath().toString().replace("\\", "/");
        
        // Find old dependency JAR and add to classpath
        String oldJarClasspath = findOldDependencyJar(commitReportDir);
        
        String adapted = javaCode;
        
        // Add classpath configuration if old JAR found
        if (oldJarClasspath != null) {
            String classpathLine = "                    launcher.getEnvironment().setSourceClasspath(new String[]{\"" + 
                                   oldJarClasspath.replace("\\", "/") + "\"});";
            // Insert classpath after setNoClasspath
            adapted = adapted.replaceAll(
                "(launcher\\.getEnvironment\\(\\)\\.setNoClasspath\\(true\\);)\\s*",
                "$1\n" + classpathLine + "\n"
            );
        }
        
        // Replace input path patterns
        adapted = adapted.replaceAll(
            "(String\\s+inputPath\\s*=\\s*)(args\\s*\\.\\s*length\\s*>\\s*0\\s*\\?\\s*args\\s*\\[\\s*0\\s*\\]\\s*:\\s*\")([^\"]+)(\"\\s*;)",
            "$1\"" + inputPath + "\";"
        );
        adapted = adapted.replaceAll(
            "(String\\s+inputPath\\s*=\\s*\")([^\"]+)(\"\\s*;)",
            "$1" + inputPath + "$3"
        );
        adapted = adapted.replaceAll(
            "(addInputResource\\s*\\(\\s*\")([^\"]+)(\"\\s*\\))",
            "$1" + inputPath + "$3"
        );
        adapted = adapted.replaceAll(
            "(addInputResource\\s*\\(\\s*)inputPath(\\s*\\))",
            "$1\"" + inputPath + "\"$2"
        );
        
        // Replace output path patterns
        adapted = adapted.replaceAll(
            "(String\\s+outputPath\\s*=\\s*)(args\\s*\\.\\s*length\\s*>\\s*1\\s*\\?\\s*args\\s*\\[\\s*1\\s*\\]\\s*:\\s*\")([^\"]+)(\"\\s*;)",
            "$1\"" + outputDirPath + "\";"
        );
        adapted = adapted.replaceAll(
            "(String\\s+outputPath\\s*=\\s*\")([^\"]+)(\"\\s*;)",
            "$1" + outputDirPath + "$3"
        );
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*\")([^\"]+)(\"\\s*\\))",
            "$1" + outputDirPath + "$3"
        );
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*)outputPath(\\s*\\))",
            "$1\"" + outputDirPath + "\"$2"
        );
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*new\\s+File\\s*\\(\\s*\")([^\"]+)(\"\\s*\\)\\s*\\))",
            "$1" + outputDirPath + "$3"
        );
        
        return adapted;
    }

    /**
     * Builds a driver skeleton if the code is not a complete class.
     */
    private String buildDriverSource(Path originalSourceFile,
                                    Path commitReportDir,
                                    Path rawRulesFile,
                                    String rules) {
        Path outputDir = commitReportDir.resolve("transformed");
        String outputDirPath = outputDir.toString().replace("\\", "/");
        String inputPath = originalSourceFile.toString().replace("\\", "/");
        
        // Find old dependency JAR and add to classpath
        String oldJarClasspath = findOldDependencyJar(commitReportDir);
        String classpathCode = oldJarClasspath != null ? 
            "                    launcher.getEnvironment().setSourceClasspath(new String[]{\"" + oldJarClasspath.replace("\\", "/") + "\"});\n" : "";
        
        // Build indented rules code
        StringBuilder rulesCode = new StringBuilder();
        String[] lines = rules.split("\n");
        for (String line : lines) {
            String adapted = line.replace("getFactory()", "factory");
            rulesCode.append("        ").append(adapted).append("\n");
        }
        
        return """
            import spoon.Launcher;
            import spoon.reflect.CtModel;
            import spoon.reflect.factory.Factory;
            import java.io.File;

            /**
             * Auto-generated Spoon driver for baseline_spoon prompt
             * Original source: %s
             * Raw rules: %s
             */
            public class SpoonApplyRules {

                public static void main(String[] args) {
                    Launcher launcher = new Launcher();
                    launcher.getEnvironment().setNoClasspath(true);
                    launcher.getEnvironment().setPrettyPrinterCreator(
                        () -> new spoon.support.sniper.SniperJavaPrettyPrinter(launcher.getEnvironment())
                    );
            %s        launcher.addInputResource("%s");
                    launcher.setSourceOutputDirectory(new File("%s"));

                    launcher.buildModel();
                    CtModel model = launcher.getModel();
                    Factory factory = launcher.getFactory();

                    applyTransformationRules(model, factory);

                    launcher.process();
                    launcher.prettyprint();
                }

                private static void applyTransformationRules(CtModel model, Factory factory) {
            %s    }
            }
            """.formatted(
                originalSourceFile.toString(),
                rawRulesFile.toString(),
                classpathCode,
                inputPath,
                outputDirPath,
                rulesCode.toString()
            );
    }

    /**
     * Builds a wrapper class that includes the processor and a main method.
     * This is used when the LLM output is a processor class without a main method.
     * Inserts the main method before the closing brace of the class.
     */
    private String buildWrapperWithMain(String processorCode, String processorClassName,
                                       Path originalSourceFile, Path commitReportDir) {
        Path outputDir = commitReportDir.resolve("transformed");
        String outputDirPath = outputDir.toAbsolutePath().toString().replace("\\", "/");
        String inputPath = originalSourceFile.toAbsolutePath().toString().replace("\\", "/");
        
        // Check if imports already include Launcher and File
        boolean needsLauncherImport = !processorCode.contains("import spoon.Launcher");
        boolean needsFileImport = !processorCode.contains("import java.io.File");
        
        // Find old dependency JAR and add to classpath
        String oldJarClasspath = findOldDependencyJar(commitReportDir);
        String classpathCode = oldJarClasspath != null ? 
            "                    launcher.getEnvironment().setSourceClasspath(new String[]{\"" + oldJarClasspath.replace("\\", "/") + "\"});\n" : "";
        
        // Template for main method
        String mainMethodTemplate = """
                
                public static void main(String[] args) {
                    Launcher launcher = new Launcher();
                    launcher.getEnvironment().setNoClasspath(true);
                    launcher.getEnvironment().setPrettyPrinterCreator(
                        () -> new spoon.support.sniper.SniperJavaPrettyPrinter(launcher.getEnvironment())
                    );
            %s        launcher.addInputResource("%s");
                    launcher.setSourceOutputDirectory(new File("%s"));

                    launcher.addProcessor(new %s());

                    try {
                        launcher.run();
                        System.out.println("Transformation completed successfully!");
                    } catch (Exception e) {
                        System.err.println("Transformation failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            """.formatted(classpathCode, inputPath, outputDirPath, processorClassName);
        
        // Add Launcher and File imports if needed
        if (needsLauncherImport || needsFileImport) {
            // Find the last import statement and add after it
            int lastImportIndex = processorCode.lastIndexOf("import ");
            if (lastImportIndex >= 0) {
                int lastImportEnd = processorCode.indexOf(";", lastImportIndex) + 1;
                String beforeImports = processorCode.substring(0, lastImportEnd);
                String afterImports = processorCode.substring(lastImportEnd);
                
                StringBuilder imports = new StringBuilder();
                if (needsLauncherImport) {
                    imports.append("\nimport spoon.Launcher;");
                }
                if (needsFileImport) {
                    imports.append("\nimport java.io.File;");
                }
                
                // Find the last closing brace of the class and insert main before it
                int lastBraceIndex = afterImports.lastIndexOf("}");
                if (lastBraceIndex >= 0) {
                    String beforeLastBrace = afterImports.substring(0, lastBraceIndex);
                    return beforeImports + imports + "\n" + beforeLastBrace + mainMethodTemplate + 
                           afterImports.substring(lastBraceIndex);
                } else {
                    return beforeImports + imports + "\n" + afterImports;
                }
            } else {
                // No imports found, add them at the beginning
                String packageDecl = extractPackage(processorCode);
                StringBuilder imports = new StringBuilder();
                if (!packageDecl.isEmpty()) {
                    imports.append(packageDecl).append("\n\n");
                }
                if (needsLauncherImport) {
                    imports.append("import spoon.Launcher;\n");
                }
                if (needsFileImport) {
                    imports.append("import java.io.File;\n");
                }
                
                // Insert main before last brace
                int lastBraceIndex = processorCode.lastIndexOf("}");
                if (lastBraceIndex >= 0) {
                    return imports + processorCode.substring(0, lastBraceIndex) + mainMethodTemplate + 
                           processorCode.substring(lastBraceIndex);
                } else {
                    return imports + processorCode;
                }
            }
        } else {
            // Imports already present, just insert main before last brace
            int lastBraceIndex = processorCode.lastIndexOf("}");
            if (lastBraceIndex >= 0) {
                return processorCode.substring(0, lastBraceIndex) + mainMethodTemplate + 
                       processorCode.substring(lastBraceIndex);
            } else {
                return processorCode;
            }
        }
    }

    /**
     * Extracts package declaration from Java code.
     */
    private String extractPackage(String javaCode) {
        Pattern pattern = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(javaCode);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    /**
     * Finds the old dependency JAR (previous version) in the commit report directory.
     * Searches for JAR files matching the pattern {artifactId}-{version}.jar
     * and returns the path to the old version JAR if found.
     * 
     * @param commitReportDir the commit report directory
     * @return the absolute path to the old dependency JAR, or null if not found
     */
    private String findOldDependencyJar(Path commitReportDir) {
        if (commitReportDir == null || !Files.exists(commitReportDir) || !Files.isDirectory(commitReportDir)) {
            return null;
        }
        
        try {
            // Look for JAR files in the commit report directory
            // Pattern: {artifactId}-{version}.jar
            return Files.list(commitReportDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .map(path -> path.toAbsolutePath().toString().replace("\\", "/"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            // If we can't list files, return null (classpath will be empty)
            return null;
        }
    }
}

