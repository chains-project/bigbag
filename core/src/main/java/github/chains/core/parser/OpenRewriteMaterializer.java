package github.chains.core.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Materializer for OpenRewrite recipe output.
 * <p>
 * OpenRewrite uses recipes (either YAML or Java classes) to define transformations.
 * This materializer:
 * <ul>
 *   <li>Extracts OpenRewrite recipes from LLM output</li>
 *   <li>Detects if it's a YAML recipe or Java recipe class</li>
 *   <li>Generates the appropriate executable file</li>
 * </ul>
 * <p>
 * OpenRewrite recipes can be:
 * - YAML files (recipe format)
 * - Java classes extending Recipe
 * - Maven/Gradle plugin configurations
 */
public final class OpenRewriteMaterializer implements Materializer {

    private static final Pattern YAML_RECIPE_PATTERN = Pattern.compile(
        "(?s)```(?:yaml|yml)?\\s*(type:\\s*specs.openrewrite.org/v1beta/recipe.*?)```",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    private static final Pattern JAVA_RECIPE_PATTERN = Pattern.compile(
        "(?s)```(?:java)?\\s*(package\\s+[\\w.]+\\s*;.*?public\\s+class\\s+\\w+.*?extends\\s+Recipe.*?\\})",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    @Override
    public String id() {
        return "openrewrite";
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
        
        Path targetDir = commitReportDir.resolve("openrewrite-rules");
        Files.createDirectories(targetDir);

        // Try to extract YAML recipe first
        String yamlRecipe = extractYamlRecipe(content);
        if (!yamlRecipe.isBlank()) {
            Path recipeFile = targetDir.resolve(baseName + "_recipe.yml");
            Files.writeString(recipeFile, yamlRecipe, StandardCharsets.UTF_8);
            
            // Generate Maven plugin configuration to run the recipe
            Path pomFile = generateMavenPluginConfig(targetDir, baseName, recipeFile);
            return pomFile;
        }

        // Try to extract Java recipe
        String javaRecipe = extractJavaRecipe(content);
        if (!javaRecipe.isBlank()) {
            String className = extractRecipeClassName(javaRecipe);
            Path recipeFile = targetDir.resolve(className + ".java");
            Files.writeString(recipeFile, javaRecipe, StandardCharsets.UTF_8);
            
            // Generate Maven plugin configuration
            Path pomFile = generateMavenPluginConfigForJava(targetDir, baseName, className);
            return pomFile;
        }

        throw new IOException("No OpenRewrite recipe found in LLM output. Expected YAML recipe or Java Recipe class.");
    }

    /**
     * Extracts YAML recipe from content.
     */
    private String extractYamlRecipe(String content) {
        Matcher matcher = YAML_RECIPE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Try to find YAML without markdown blocks
        if (content.contains("type: specs.openrewrite.org/v1beta/recipe")) {
            return content.trim();
        }
        
        return "";
    }

    /**
     * Extracts Java recipe class from content.
     */
    private String extractJavaRecipe(String content) {
        Matcher matcher = JAVA_RECIPE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Try to find raw Java code
        if (content.contains("extends Recipe") || content.contains("implements Recipe")) {
            String trimmed = content.trim();
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                return trimmed;
            }
        }
        
        return "";
    }

    /**
     * Extracts recipe class name from Java code.
     */
    private String extractRecipeClassName(String javaCode) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");
        Matcher matcher = pattern.matcher(javaCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "OpenRewriteRecipe";
    }

    /**
     * Generates Maven plugin configuration for YAML recipe.
     */
    private Path generateMavenPluginConfig(Path targetDir, String baseName, Path recipeFile) throws IOException {
        Path pomFile = targetDir.resolve("pom-openrewrite.xml");
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>github.chains</groupId>
                <artifactId>openrewrite-runner</artifactId>
                <version>1.0.0</version>
                
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.openrewrite.maven</groupId>
                            <artifactId>rewrite-maven-plugin</artifactId>
                            <version>5.40.0</version>
                            <configuration>
                                <activeRecipes>
                                    <recipe>%s</recipe>
                                </activeRecipes>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.formatted(recipeFile.getFileName().toString());
        
        Files.writeString(pomFile, pomContent, StandardCharsets.UTF_8);
        return pomFile;
    }

    /**
     * Generates Maven plugin configuration for Java recipe.
     */
    private Path generateMavenPluginConfigForJava(Path targetDir, String baseName, String className) throws IOException {
        Path pomFile = targetDir.resolve("pom-openrewrite.xml");
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>github.chains</groupId>
                <artifactId>openrewrite-runner</artifactId>
                <version>1.0.0</version>
                
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.openrewrite.maven</groupId>
                            <artifactId>rewrite-maven-plugin</artifactId>
                            <version>5.40.0</version>
                            <configuration>
                                <activeRecipes>
                                    <recipe>%s</recipe>
                                </activeRecipes>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.formatted(className);
        
        Files.writeString(pomFile, pomContent, StandardCharsets.UTF_8);
        return pomFile;
    }
}

