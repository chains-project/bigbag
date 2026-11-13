package github.chains;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generic transformation for fixing PlexusContainer.getLoggerManager() breaking change.
 * 
 * Breaking change: In plexus-container-default 2.1.1, getLoggerManager() method
 * was removed from PlexusContainer interface and moved to MutablePlexusContainer interface.
 * 
 * Transformation: Wraps PlexusContainer instances with cast to MutablePlexusContainer
 * when calling getLoggerManager() method.
 * 
 * Pattern: [any expression of type PlexusContainer].getLoggerManager()
 * Replacement: ((MutablePlexusContainer) [expression]).getLoggerManager()
 * 
 * This transformation is generic and can be applied to any Java project affected
 * by this breaking change by changing the input source directory path.
 */
public class Main {
    
    // Regular expression patterns for matching getLoggerManager() calls
    private static final Pattern[] PATTERNS = {
        // Pattern 1: getContainer().getLoggerManager()
        Pattern.compile("(\\w+)\\s*\\(\\s*\\)\\s*\\.\\s*getLoggerManager\\s*\\(\\s*\\)"),
        
        // Pattern 2: variable.getLoggerManager() where variable might be of type PlexusContainer
        Pattern.compile("(\\b\\w+\\b)\\s*\\.\\s*getLoggerManager\\s*\\(\\s*\\)"),
        
        // Pattern 3: methodCall().getLoggerManager() 
        Pattern.compile("(\\b[a-zA-Z_$][a-zA-Z0-9_$]*\\s*\\([^)]*\\))\\s*\\.\\s*getLoggerManager\\s*\\(\\s*\\)"),
    };
    
    private static final String CAST_PREFIX = "((org.codehaus.plexus.MutablePlexusContainer) ";
    private static final String IMPORT_STATEMENT = "import org.codehaus.plexus.MutablePlexusContainer;";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("\nDescription:");
            System.err.println("  Applies transformation to fix PlexusContainer.getLoggerManager() breaking change.");
            System.err.println("  The getLoggerManager() method was moved from PlexusContainer interface");
            System.err.println("  to MutablePlexusContainer interface in version 2.1.1.");
            System.err.println("\nTransformation:");
            System.err.println("  Changes: container.getLoggerManager()");
            System.err.println("  To:      ((MutablePlexusContainer) container).getLoggerManager()");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: Adding cast to MutablePlexusContainer for getLoggerManager() calls");
        
        Path sourcePath = Paths.get(sourceDir);
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalReplacements = 0;
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
            String originalContent = content;
            
            // Apply each pattern
            for (Pattern pattern : PATTERNS) {
                java.util.regex.Matcher matcher = pattern.matcher(content);
                StringBuffer sb = new StringBuffer();
                int replacementsInFile = 0;
                
                while (matcher.find()) {
                    String matchedExpr = matcher.group(1);
                    // Don't modify if already cast
                    if (!matchedExpr.startsWith("((") || !matchedExpr.contains("MutablePlexusContainer")) {
                        String replacement = CAST_PREFIX + matchedExpr + ").getLoggerManager()";
                        matcher.appendReplacement(sb, replacement);
                        replacementsInFile++;
                    } else {
                        matcher.appendReplacement(sb, matcher.group(0));
                    }
                }
                matcher.appendTail(sb);
                content = sb.toString();
                
                if (replacementsInFile > 0) {
                    totalReplacements += replacementsInFile;
                    System.out.println("  File: " + javaFile + " - Made " + replacementsInFile + " replacement(s)");
                }
            }
            
            if (!content.equals(originalContent)) {
                filesModified++;
                
                // Add import if needed
                if (content.contains("MutablePlexusContainer") && 
                    !content.contains("import org.codehaus.plexus.MutablePlexusContainer")) {
                    
                    // Find package statement and add import after it
                    int packageIndex = content.indexOf("package ");
                    if (packageIndex != -1) {
                        int packageEnd = content.indexOf(";", packageIndex) + 1;
                        if (packageEnd > 0) {
                            String beforePackage = content.substring(0, packageEnd);
                            String afterPackage = content.substring(packageEnd);
                            
                            // Check if there are already imports
                            int firstImport = afterPackage.indexOf("import ");
                            if (firstImport != -1) {
                                // Insert before first import
                                String beforeImport = afterPackage.substring(0, firstImport);
                                String afterImport = afterPackage.substring(firstImport);
                                content = beforePackage + beforeImport + IMPORT_STATEMENT + "\n" + afterImport;
                            } else {
                                // No imports, add after package
                                content = beforePackage + "\n" + IMPORT_STATEMENT + afterPackage;
                            }
                        }
                    }
                }
                
                Files.write(javaFile, content.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        System.out.println("\nTransformation Summary:");
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Total replacements: " + totalReplacements);
        System.out.println("  Transformation completed successfully!");
        
        if (totalReplacements == 0) {
            System.out.println("\nNote: No getLoggerManager() calls found to fix.");
            System.out.println("This could mean:");
            System.out.println("  1. The project doesn't use getLoggerManager()");
            System.out.println("  2. The calls are already fixed");
            System.out.println("  3. The calls use a different pattern not matched by this transformation");
        }
    }
}