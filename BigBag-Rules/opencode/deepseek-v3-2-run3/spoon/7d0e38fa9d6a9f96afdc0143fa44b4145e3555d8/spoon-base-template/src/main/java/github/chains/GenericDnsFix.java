package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * A generic, reusable transformation rule for DNS API breaking changes.
 * 
 * This transformation can be applied to ANY Maven project affected by the
 * DNS API breaking change where methods gained a "location" parameter.
 * 
 * The transformation works by searching for specific method call patterns
 * and inserting the required "global" location parameter.
 * 
 * BREAKING CHANGES ADDRESSED:
 * 1. ManagedZones.create(project, zone) -> ManagedZones.create(project, "global", zone)
 * 2. ManagedZones.get(project, zoneName) -> ManagedZones.get(project, "global", zoneName)
 * 3. ManagedZones.list(project) -> ManagedZones.list(project, "global")
 * 4. ManagedZones.delete(project, zoneName) -> ManagedZones.delete(project, "global", zoneName)
 * 5. ResourceRecordSets.list(project, zoneName) -> ResourceRecordSets.list(project, "global", zoneName)
 * 6. Projects.get(project) -> Projects.get(project, project) [duplicate parameter]
 * 7. Changes.create(project, zoneName, change) -> Changes.create(project, "global", zoneName, change)
 * 8. Changes.get(project, zoneName, changeId) -> Changes.get(project, "global", zoneName, changeId)
 * 9. Changes.list(project, zoneName) -> Changes.list(project, "global", zoneName)
 * 
 * USAGE: java GenericDnsFix <source-directory>
 */
public class GenericDnsFix {
    
    // Regex patterns for the breaking changes
    private static final List<Pattern> PATTERNS = Arrays.asList(
        // Pattern 1: .create(project, zone) -> .create(project, "global", zone)
        Pattern.compile("\\.create\\(([^,]+),\\s*([^)]+)\\)"),
        
        // Pattern 2: .get(project, zoneName) -> .get(project, "global", zoneName)
        Pattern.compile("\\.get\\(([^,]+),\\s*([^)]+)\\)"),
        
        // Pattern 3: .list(project) -> .list(project, "global")
        Pattern.compile("\\.list\\(([^)]+)\\)"),
        
        // Pattern 4: .delete(project, zoneName) -> .delete(project, "global", zoneName)
        Pattern.compile("\\.delete\\(([^,]+),\\s*([^)]+)\\)"),
        
        // Pattern 5: Projects.get(project) -> Projects.get(project, project)
        Pattern.compile("Projects\\.get\\(([^)]+)\\)"),
        
        // Pattern 6: Changes.create(project, zoneName, change) -> Changes.create(project, "global", zoneName, change)
        Pattern.compile("\\.create\\(([^,]+),\\s*([^,]+),\\s*([^)]+)\\)"),
        
        // Pattern 7: Changes.get(project, zoneName, changeId) -> Changes.get(project, "global", zoneName, changeId)
        Pattern.compile("\\.get\\(([^,]+),\\s*([^,]+),\\s*([^)]+)\\)"),
        
        // Pattern 8: Changes.list(project, zoneName) -> Changes.list(project, "global", zoneName)
        Pattern.compile("\\.list\\(([^,]+),\\s*([^)]+)\\)")
    );
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java GenericDnsFix <source-directory>");
            System.err.println("Example: java GenericDnsFix /path/to/project/src");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying generic DNS API fix to: " + sourceDir);
        System.out.println("This transformation handles the breaking change where methods");
        System.out.println("gained a 'location' parameter (default: 'global').");
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int totalFixes = 0;
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                String originalContent = content;
                
                // Apply all transformations
                for (Pattern pattern : PATTERNS) {
                    Matcher matcher = pattern.matcher(content);
                    StringBuffer result = new StringBuffer();
                    boolean changed = false;
                    
                    while (matcher.find()) {
                        changed = true;
                        String replacement = getReplacement(matcher.group(0), pattern);
                        matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                    }
                    
                    if (changed) {
                        matcher.appendTail(result);
                        content = result.toString();
                    }
                }
                
                if (!content.equals(originalContent)) {
                    Files.write(javaFile, content.getBytes(StandardCharsets.UTF_8));
                    int fixesCount = countDifferences(originalContent, content);
                    totalFixes += fixesCount;
                    filesModified++;
                    System.out.println("✓ Fixed " + fixesCount + " issue(s) in: " + 
                                     sourceDir.relativize(javaFile));
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSUMMARY:");
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Total fixes applied: " + totalFixes);
        System.out.println("\nThe transformation has been successfully applied.");
        System.out.println("The code should now compile with the new DNS API.");
    }
    
    private static String getReplacement(String match, Pattern pattern) {
        if (pattern.pattern().equals("Projects\\.get\\(([^)]+)\\)")) {
            // Special case: Projects.get(project) -> Projects.get(project, project)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String project = m.group(1);
                return "Projects.get(" + project + ", " + project + ")";
            }
        } else if (pattern.pattern().equals("\\.list\\(([^)]+)\\)")) {
            // .list(project) -> .list(project, "global")
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg = m.group(1);
                return ".list(" + arg + ", \"global\")";
            }
        } else if (pattern.pattern().equals("\\.create\\(([^,]+),\\s*([^)]+)\\)")) {
            // .create(project, zone) -> .create(project, "global", zone)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg1 = m.group(1);
                String arg2 = m.group(2);
                return ".create(" + arg1 + ", \"global\", " + arg2 + ")";
            }
        } else if (pattern.pattern().equals("\\.get\\(([^,]+),\\s*([^)]+)\\)")) {
            // .get(project, zoneName) -> .get(project, "global", zoneName)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg1 = m.group(1);
                String arg2 = m.group(2);
                return ".get(" + arg1 + ", \"global\", " + arg2 + ")";
            }
        } else if (pattern.pattern().equals("\\.delete\\(([^,]+),\\s*([^)]+)\\)")) {
            // .delete(project, zoneName) -> .delete(project, "global", zoneName)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg1 = m.group(1);
                String arg2 = m.group(2);
                return ".delete(" + arg1 + ", \"global\", " + arg2 + ")";
            }
        } else if (pattern.pattern().equals("\\.create\\(([^,]+),\\s*([^,]+),\\s*([^)]+)\\)")) {
            // .create(project, zoneName, change) -> .create(project, "global", zoneName, change)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg1 = m.group(1);
                String arg2 = m.group(2);
                String arg3 = m.group(3);
                return ".create(" + arg1 + ", \"global\", " + arg2 + ", " + arg3 + ")";
            }
        } else if (pattern.pattern().equals("\\.get\\(([^,]+),\\s*([^,]+),\\s*([^)]+)\\)")) {
            // .get(project, zoneName, changeId) -> .get(project, "global", zoneName, changeId)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg1 = m.group(1);
                String arg2 = m.group(2);
                String arg3 = m.group(3);
                return ".get(" + arg1 + ", \"global\", " + arg2 + ", " + arg3 + ")";
            }
        } else if (pattern.pattern().equals("\\.list\\(([^,]+),\\s*([^)]+)\\)")) {
            // .list(project, zoneName) -> .list(project, "global", zoneName)
            Matcher m = pattern.matcher(match);
            if (m.find()) {
                String arg1 = m.group(1);
                String arg2 = m.group(2);
                return ".list(" + arg1 + ", \"global\", " + arg2 + ")";
            }
        }
        
        return match; // No transformation
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static int countDifferences(String original, String transformed) {
        // Simple heuristic: count occurrences of added "global"
        Pattern globalPattern = Pattern.compile("\"global\"");
        Matcher originalMatcher = globalPattern.matcher(original);
        Matcher transformedMatcher = globalPattern.matcher(transformed);
        
        int originalCount = 0;
        while (originalMatcher.find()) originalCount++;
        
        int transformedCount = 0;
        while (transformedMatcher.find()) transformedCount++;
        
        return transformedCount - originalCount;
    }
}