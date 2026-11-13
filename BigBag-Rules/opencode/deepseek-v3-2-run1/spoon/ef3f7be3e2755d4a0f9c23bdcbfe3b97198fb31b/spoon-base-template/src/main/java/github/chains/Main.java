package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generic transformation rule for tinspin-indexes 1.x to 2.0 API migration.
 * This transformation handles the breaking changes in a generic, reusable way.
 */
public class Main {
    
    // Transformation patterns for tinspin-indexes 2.0 API
    private static final List<TransformationRule> RULES = Arrays.asList(
        // Import transformations
        new TransformationRule(
            Pattern.compile("import\\s+org\\.tinspin\\.index\\.PointIndex\\s*;"),
            "import org.tinspin.index.PointMap;"
        ),
        new TransformationRule(
            Pattern.compile("import\\s+org\\.tinspin\\.index\\.PointDistanceFunction\\s*;"),
            "import org.tinspin.index.PointDistance;"
        ),
        new TransformationRule(
            Pattern.compile("import\\s+org\\.tinspin\\.index\\.PointEntryDist\\s*;"),
            "import org.tinspin.index.Index$PointEntryKnn;"
        ),
        
        // Type reference transformations
        new TransformationRule(
            Pattern.compile("\\bPointIndex\\s*<([^>]+)>"),
            "PointMap<$1>"
        ),
        new TransformationRule(
            Pattern.compile("\\bPointDistanceFunction\\b"),
            "PointDistance"
        ),
        new TransformationRule(
            Pattern.compile("\\bPointEntryDist\\s*<([^>]+)>"),
            "Index$PointEntryKnn<$1>"
        ),
        
        // Method invocation transformations
        // KDTree.create(2, distanceFunction) -> KDTree.create(2)
        new TransformationRule(
            Pattern.compile("KDTree\\.create\\(\\s*(\\d+)\\s*,\\s*[^)]+\\)"),
            "KDTree.create($1)"
        ),
        
        // CoverTree.create() signature already compatible, just type checking needed
        // No transformation needed for CoverTree.create()
    );
    
    static class TransformationRule {
        final Pattern pattern;
        final String replacement;
        
        TransformationRule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.err.println("This transformation fixes tinspin-indexes 1.x to 2.0 API breaking changes:");
            System.err.println("  - PointIndex<T> -> PointMap<T>");
            System.err.println("  - PointDistanceFunction -> PointDistance");
            System.err.println("  - PointEntryDist<T> -> Index$PointEntryKnn<T>");
            System.err.println("  - KDTree.create(dims, distanceFunction) -> KDTree.create(dims)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming tinspin-indexes 1.x to 2.0 API in: " + sourceDir);
        
        try {
            int totalChanges = transformDirectory(new File(sourceDir));
            System.out.println("Transformation completed! Total files modified: " + totalChanges);
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformDirectory(File dir) throws IOException {
        int totalChanges = 0;
        
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Source directory does not exist: " + dir.getPath());
        }
        
        // Find all Java files
        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(dir, javaFiles);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (File file : javaFiles) {
            int changes = transformFile(file);
            if (changes > 0) {
                totalChanges++;
                System.out.println("  Modified: " + file.getPath() + " (" + changes + " changes)");
            }
        }
        
        return totalChanges;
    }
    
    private static void collectJavaFiles(File dir, List<File> javaFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                collectJavaFiles(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    private static int transformFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        String originalContent = content;
        
        // Apply all transformation rules
        for (TransformationRule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, rule.replacement);
            }
            matcher.appendTail(sb);
            content = sb.toString();
        }
        
        // Check if content changed
        if (!content.equals(originalContent)) {
            // Write back transformed content
            Files.write(file.toPath(), content.getBytes());
            return countChanges(originalContent, content);
        }
        
        return 0;
    }
    
    private static int countChanges(String original, String transformed) {
        // Simple change count by comparing lines
        String[] origLines = original.split("\n");
        String[] transLines = transformed.split("\n");
        
        int changes = 0;
        for (int i = 0; i < Math.min(origLines.length, transLines.length); i++) {
            if (!origLines[i].equals(transLines[i])) {
                changes++;
            }
        }
        return changes + Math.abs(origLines.length - transLines.length);
    }
}