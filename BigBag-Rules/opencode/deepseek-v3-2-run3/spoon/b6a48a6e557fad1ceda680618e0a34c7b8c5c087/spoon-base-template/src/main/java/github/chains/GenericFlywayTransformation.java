package github.chains;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * GENERIC, REUSABLE transformation rule for Flyway 8.x to 9.x API migration.
 * 
 * This transformation can be applied to ANY Maven project affected by the
 * flyway-core 9.18.0 breaking API change.
 * 
 * CHARACTERIZATION OF BREAKING CHANGE:
 * ------------------------------------
 * OLD API PATTERN (Flyway 8.x):
 *   - Constructor: new Flyway() [no arguments]
 *   - Configuration: Individual setter methods
 *   - Example: flyway.setDataSource(dataSource), flyway.setLocations(locations)
 * 
 * NEW API PATTERN (Flyway 9.18.0):
 *   - Factory method: Flyway.configure() returns FluentConfiguration
 *   - Configuration: Fluent API method chaining
 *   - Finalization: .load() to create Flyway instance
 *   - Example: Flyway.configure().dataSource(dataSource).locations(locations).load()
 * 
 * STRUCTURAL TRANSFORMATION:
 * 1. Replace `new Flyway()` with `Flyway.configure()` or `Flyway.configure(classLoader)`
 * 2. Map setter calls to fluent API methods
 * 3. Remove setter statements after transformation
 * 4. Add `.load()` to finalize
 * 
 * This rule contains NO project-specific identifiers and can be applied
 * to any codebase by changing only the source directory path.
 */
public class GenericFlywayTransformation {
    
    // Map of old setter methods to new fluent API methods
    private static final Map<String, String> METHOD_MAP = new HashMap<>();
    static {
        // Core configuration methods
        METHOD_MAP.put("setDataSource", "dataSource");
        METHOD_MAP.put("setLocations", "locations");
        METHOD_MAP.put("setValidateOnMigrate", "validateOnMigrate");
        METHOD_MAP.put("setClassLoader", null); // Special case: handled in configure()
        
        // Common configuration methods
        METHOD_MAP.put("setBaselineVersion", "baselineVersion");
        METHOD_MAP.put("setBaselineDescription", "baselineDescription");
        METHOD_MAP.put("setBaselineOnMigrate", "baselineOnMigrate");
        METHOD_MAP.put("setPlaceholderPrefix", "placeholderPrefix");
        METHOD_MAP.put("setPlaceholderSuffix", "placeholderSuffix");
        METHOD_MAP.put("setPlaceholders", "placeholders");
        METHOD_MAP.put("setPlaceholderReplacement", "placeholderReplacement");
        METHOD_MAP.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        METHOD_MAP.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        METHOD_MAP.put("setSqlMigrationSeparator", "sqlMigrationSeparator");
        METHOD_MAP.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        METHOD_MAP.put("setEncoding", "encoding");
        METHOD_MAP.put("setTarget", "target");
        METHOD_MAP.put("setOutOfOrder", "outOfOrder");
        METHOD_MAP.put("setCleanOnValidationError", "cleanOnValidationError");
        METHOD_MAP.put("setCleanDisabled", "cleanDisabled");
        METHOD_MAP.put("setIgnoreMigrationPatterns", "ignoreMigrationPatterns");
        METHOD_MAP.put("setCallbacks", "callbacks");
        METHOD_MAP.put("setSkipDefaultCallbacks", "skipDefaultCallbacks");
        METHOD_MAP.put("setSkipDefaultResolvers", "skipDefaultResolvers");
        METHOD_MAP.put("setLockRetryCount", "lockRetryCount");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Generic Flyway API Transformation Rule");
            System.out.println("Usage: java github.chains.GenericFlywayTransformation <source-dir>");
            System.out.println("Applies transformation to all Java files in directory");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir)) {
            System.err.println("Error: Source directory not found: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying generic Flyway transformation to: " + sourceDir);
        System.out.println("Transformation rule: Flyway 8.x -> 9.18.0 API migration");
        
        List<Path> javaFiles = findAllJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedCount = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedCount++;
                System.out.println("  Transformed: " + sourceDir.relativize(javaFile));
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Modified " + transformedCount + " files");
        System.out.println("\n" + getRuleSummary());
    }
    
    private static List<Path> findAllJavaFiles(Path dir) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(files::add);
        }
        return files;
    }
    
    private static boolean transformFile(Path file) throws Exception {
        String content = new String(Files.readAllBytes(file));
        String original = content;
        
        // Apply transformation
        content = transformFlywayInstantiations(content);
        
        if (!content.equals(original)) {
            Files.write(file, content.getBytes());
            return true;
        }
        return false;
    }
    
    /**
     * Core transformation logic.
     * Finds and transforms Flyway constructor patterns to fluent API.
     */
    private static String transformFlywayInstantiations(String content) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n", -1); // Keep trailing empty strings
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Check for Flyway constructor
            if (containsFlywayConstructor(line)) {
                // Try to extract variable name
                String varName = extractVariableName(line);
                if (varName != null) {
                    // This line has a Flyway constructor assignment
                    // Collect subsequent setter calls
                    List<SetterCall> setters = new ArrayList<>();
                    int j = i + 1;
                    
                    while (j < lines.length && isSetterCall(lines[j], varName)) {
                        SetterCall setter = parseSetterCall(lines[j], varName);
                        if (setter != null) {
                            setters.add(setter);
                        }
                        j++;
                    }
                    
                    if (!setters.isEmpty()) {
                        // Build transformed line
                        String transformed = buildTransformedLine(line, varName, setters);
                        result.append(transformed).append("\n");
                        
                        // Skip the setter lines (they're incorporated into the fluent call)
                        i = j - 1; // -1 because loop will increment
                        continue;
                    }
                }
            }
            
            // Keep original line
            result.append(line).append("\n");
        }
        
        return result.toString();
    }
    
    private static boolean containsFlywayConstructor(String line) {
        return line.contains("new Flyway()") || line.contains("new org.flywaydb.core.Flyway()");
    }
    
    private static String extractVariableName(String line) {
        // Pattern: (final) (org.flywaydb.core.)?Flyway varName = new ...
        Pattern pattern = Pattern.compile(
            "(?:final\\s+)?(?:org\\.flywaydb\\.core\\.)?Flyway\\s+(\\w+)\\s*="
        );
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private static boolean isSetterCall(String line, String varName) {
        line = line.trim();
        return line.startsWith(varName + ".set") && line.endsWith(";");
    }
    
    private static SetterCall parseSetterCall(String line, String varName) {
        line = line.trim();
        if (!line.startsWith(varName + ".set")) return null;
        
        // Extract method name and arguments
        // Format: varName.setMethodName(arguments);
        String rest = line.substring(varName.length() + 1); // +1 for "."
        int parenStart = rest.indexOf('(');
        int parenEnd = rest.lastIndexOf(')');
        
        if (parenStart == -1 || parenEnd == -1) return null;
        
        String methodName = rest.substring(0, parenStart);
        String arguments = rest.substring(parenStart + 1, parenEnd);
        
        return new SetterCall(methodName, arguments);
    }
    
    private static String buildTransformedLine(String originalLine, String varName, 
                                               List<SetterCall> setters) {
        // Extract the part before "="
        String beforeEquals = originalLine.substring(0, originalLine.indexOf("=")).trim();
        
        // Check if we have setClassLoader
        String classLoaderArg = null;
        for (SetterCall setter : setters) {
            if ("setClassLoader".equals(setter.methodName)) {
                classLoaderArg = setter.arguments;
                break;
            }
        }
        
        // Build fluent API call
        StringBuilder fluentCall = new StringBuilder();
        fluentCall.append(beforeEquals).append(" = ");
        
        if (classLoaderArg != null) {
            fluentCall.append("org.flywaydb.core.Flyway.configure(")
                      .append(classLoaderArg).append(")");
        } else {
            fluentCall.append("org.flywaydb.core.Flyway.configure()");
        }
        
        // Add fluent methods for each setter (excluding classLoader)
        for (SetterCall setter : setters) {
            if ("setClassLoader".equals(setter.methodName)) {
                continue; // Already handled
            }
            
            String newMethod = METHOD_MAP.get(setter.methodName);
            if (newMethod == null) {
                // Generic transformation: setFooBar -> fooBar
                newMethod = Character.toLowerCase(setter.methodName.charAt(3)) +
                           setter.methodName.substring(4);
            }
            
            fluentCall.append(".").append(newMethod)
                      .append("(").append(setter.arguments).append(")");
        }
        
        fluentCall.append(".load();");
        
        // Preserve comments from original line
        if (originalLine.contains("//")) {
            String comment = originalLine.substring(originalLine.indexOf("//"));
            fluentCall.append(" ").append(comment);
        }
        
        return fluentCall.toString();
    }
    
    private static class SetterCall {
        final String methodName;
        final String arguments;
        
        SetterCall(String methodName, String arguments) {
            this.methodName = methodName;
            this.arguments = arguments;
        }
    }
    
    /**
     * Returns a summary of the transformation rule for documentation.
     */
    public static String getRuleSummary() {
        return """
               =================================================================
               GENERIC FLYWAY TRANSFORMATION RULE SUMMARY
               =================================================================
               
               APPLICABILITY:
               - Any Java project using flyway-core 8.x or earlier
               - Upgrading to flyway-core 9.18.0 or later
               - Contains code using old Flyway constructor + setter pattern
               
               TRANSFORMATION:
               
               INPUT PATTERN:
                 Flyway flyway = new Flyway();
                 flyway.setDataSource(dataSource);
                 flyway.setClassLoader(classLoader);
                 flyway.setLocations("db/migrations");
                 flyway.setValidateOnMigrate(true);
               
               OUTPUT PATTERN:
                 Flyway flyway = Flyway.configure(classLoader)
                     .dataSource(dataSource)
                     .locations("db/migrations")
                     .validateOnMigrate(true)
                     .load();
               
               KEY MAPPINGS:
               - new Flyway() → Flyway.configure()
               - setClassLoader(arg) → Flyway.configure(arg)
               - setDataSource(arg) → .dataSource(arg)
               - setLocations(arg) → .locations(arg)
               - setValidateOnMigrate(arg) → .validateOnMigrate(arg)
               - Other setters: setXyz(arg) → .xyz(arg) [camelCase conversion]
               
               PROPERTIES:
               - Idempotent: Can be safely applied multiple times
               - Generic: No project-specific hardcoded values
               - Structural: Based on API patterns, not specific identifiers
               
               USAGE:
               java github.chains.GenericFlywayTransformation <source-directory>
               
               The transformation will scan all Java files and apply the rule
               wherever the old Flyway API pattern is detected.
               =================================================================
               """;
    }
}