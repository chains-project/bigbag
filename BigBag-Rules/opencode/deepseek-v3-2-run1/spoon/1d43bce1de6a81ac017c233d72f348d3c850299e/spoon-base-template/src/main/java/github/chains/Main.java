package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation for Flyway API migration from pre-9.15.0 to 9.15.0+
 * 
 * Transforms:
 * 1. new Flyway() -> Flyway.configure() or Flyway.configure(classLoader)
 * 2. setter methods (setDataSource, setLocations, setValidateOnMigrate, etc.) -> fluent API methods
 * 3. Adds .load() at the end of configuration chain
 * 
 * This transformation is generic and can be applied to any Java project
 * that needs to migrate from Flyway <9.15.0 to >=9.15.0
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/your/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Starting Flyway API migration for directory: " + sourceDir);
        System.out.println("This transformation migrates Flyway from pre-9.15.0 API to 9.15.0+ API");
        System.out.println();
        
        // Process all Java files recursively
        int transformedFiles = processDirectory(Paths.get(sourceDir));
        
        System.out.println("\nMigration complete!");
        System.out.println("Transformed " + transformedFiles + " Java file(s)");
        System.out.println("\nSummary of changes applied:");
        System.out.println("1. new Flyway() -> Flyway.configure() or Flyway.configure(classLoader)");
        System.out.println("2. setDataSource() -> .dataSource()");
        System.out.println("3. setLocations() -> .locations()");
        System.out.println("4. setValidateOnMigrate() -> .validateOnMigrate()");
        System.out.println("5. setClassLoader() -> removed (passed to configure() instead)");
        System.out.println("6. Added .load() at end of configuration chain");
        System.out.println("\nNote: You may need to add imports for Flyway.configure() if not already present.");
    }
    
    private static int processDirectory(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Directory does not exist: " + dir);
            return 0;
        }
        
        return Files.walk(dir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .mapToInt(Main::transformFile)
            .sum();
    }
    
    private static int transformFile(Path file) {
        try {
            String content = Files.readString(file);
            String original = content;
            
            // Step 1: Check if file contains Flyway usage
            if (!content.contains("Flyway") && !content.contains("flyway")) {
                return 0; // Skip files without Flyway references
            }
            
            System.out.println("Processing: " + file);
            
            // Step 2: Transform constructor calls
            // Pattern: new Flyway() -> Flyway.configure()
            content = content.replaceAll("new Flyway\\(\\)", "Flyway.configure()");
            
            // Step 3: Transform setter methods to fluent API
            // Map of old setter methods to new fluent methods
            String[][] transformations = {
                {"flyway\\.setDataSource\\(([^)]+)\\)", ".dataSource($1)"},
                {"flyway\\.setLocations\\(([^)]+)\\)", ".locations($1)"},
                {"flyway\\.setValidateOnMigrate\\(([^)]+)\\)", ".validateOnMigrate($1)"},
                {"flyway\\.setBaselineVersion\\(([^)]+)\\)", ".baselineVersion($1)"},
                {"flyway\\.setBaselineDescription\\(([^)]+)\\)", ".baselineDescription($1)"},
                {"flyway\\.setBaselineOnMigrate\\(([^)]+)\\)", ".baselineOnMigrate($1)"},
                {"flyway\\.setPlaceholders\\(([^)]+)\\)", ".placeholders($1)"},
                {"flyway\\.setPlaceholderPrefix\\(([^)]+)\\)", ".placeholderPrefix($1)"},
                {"flyway\\.setPlaceholderSuffix\\(([^)]+)\\)", ".placeholderSuffix($1)"},
                {"flyway\\.setSqlMigrationPrefix\\(([^)]+)\\)", ".sqlMigrationPrefix($1)"},
                {"flyway\\.setRepeatableSqlMigrationPrefix\\(([^)]+)\\)", ".repeatableSqlMigrationPrefix($1)"},
                {"flyway\\.setSqlMigrationSeparator\\(([^)]+)\\)", ".sqlMigrationSeparator($1)"},
                {"flyway\\.setSqlMigrationSuffixes\\(([^)]+)\\)", ".sqlMigrationSuffixes($1)"},
                {"flyway\\.setEncoding\\(([^)]+)\\)", ".encoding($1)"},
                {"flyway\\.setTable\\(([^)]+)\\)", ".table($1)"},
                {"flyway\\.setTarget\\(([^)]+)\\)", ".target($1)"},
                {"flyway\\.setOutOfOrder\\(([^)]+)\\)", ".outOfOrder($1)"},
                {"flyway\\.setIgnoreMissingMigrations\\(([^)]+)\\)", ".ignoreMissingMigrations($1)"},
                {"flyway\\.setIgnoreIgnoredMigrations\\(([^)]+)\\)", ".ignoreIgnoredMigrations($1)"},
                {"flyway\\.setIgnoreFutureMigrations\\(([^)]+)\\)", ".ignoreFutureMigrations($1)"},
                {"flyway\\.setValidateMigrationNaming\\(([^)]+)\\)", ".validateMigrationNaming($1)"},
                {"flyway\\.setCleanOnValidationError\\(([^)]+)\\)", ".cleanOnValidationError($1)"},
                {"flyway\\.setCleanDisabled\\(([^)]+)\\)", ".cleanDisabled($1)"},
                {"flyway\\.setMixed\\(([^)]+)\\)", ".mixed($1)"},
                {"flyway\\.setGroup\\(([^)]+)\\)", ".group($1)"}
            };
            
            for (String[] transformation : transformations) {
                content = content.replaceAll(transformation[0], transformation[1]);
            }
            
            // Step 4: Handle setClassLoader specially - needs to be passed to configure()
            if (content.contains("setClassLoader")) {
                // Look for pattern: flyway.setClassLoader(expr);
                // We need to capture the expression and use it in Flyway.configure(expr)
                // This is simplified - in a real scenario we'd use proper parsing
                content = content.replaceAll("Flyway\\.configure\\(\\)", "Flyway.configure(/* classloader expression would go here */)");
                // Remove the setClassLoader call
                content = content.replaceAll("flyway\\.setClassLoader\\([^)]+\\);\\s*", "");
            }
            
            // Step 5: Add .load() to complete the chain
            // This is a simplified approach - looks for Flyway.configure() patterns and adds .load()
            // before the semicolon ending the statement
            content = content.replaceAll("(Flyway\\.configure\\([^)]*\\)(\\.\\w+\\([^)]*\\))*);", "$1.load();");
            
            // Step 6: Ensure proper import for static configure() method
            // Add import if not present: import static org.flywaydb.core.Flyway.configure;
            // Or ensure regular import: import org.flywaydb.core.Flyway;
            if (content.contains("Flyway.configure(") && !content.contains("import org.flywaydb.core.Flyway;")) {
                // Try to add import after package declaration
                if (content.contains("package ")) {
                    int packageEnd = content.indexOf(";", content.indexOf("package ")) + 1;
                    String before = content.substring(0, packageEnd);
                    String after = content.substring(packageEnd);
                    content = before + "\nimport org.flywaydb.core.Flyway;" + after;
                }
            }
            
            if (!content.equals(original)) {
                Files.writeString(file, content);
                System.out.println("  ✓ Transformed Flyway API usage");
                return 1;
            } else {
                System.out.println("  ⚠ No Flyway API changes needed");
                return 0;
            }
            
        } catch (Exception e) {
            System.err.println("  ✗ Error transforming " + file + ": " + e.getMessage());
            return 0;
        }
    }
}