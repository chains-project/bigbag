package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generic, reusable transformation rule for fixing tinspin-indexes 1.x to 2.0 API breaking changes.
 * This transformation can be applied to ANY Maven project affected by the same breaking change.
 * 
 * Breaking Changes Fixed:
 * 1. PointIndex<T> interface removed → Replaced by PointMap<T>
 * 2. PointDistanceFunction interface removed → Replaced by PointDistance  
 * 3. PointEntryDist<T> class removed → Replaced by Index$PointEntryKnn<T>
 * 4. KDTree.create(dims, distanceFunction) signature changed → KDTree.create(dims)
 * 5. CoverTree.create() uses PointDistance instead of PointDistanceFunction (compatible)
 * 
 * The transformation matches the old API pattern structurally and applies the fix
 * wherever the old pattern appears, parameterized by fully-qualified type names
 * from the dependency, NOT from the client.
 */
public class Tinspin20Transformer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Tinspin-indexes 2.0 API Transformation Tool");
            System.out.println("Usage: java Tinspin20Transformer <source-directory>");
            System.out.println();
            System.out.println("This tool fixes the following breaking changes from tinspin-indexes 1.x to 2.0:");
            System.out.println("  • org.tinspin.index.PointIndex<T>      → org.tinspin.index.PointMap<T>");
            System.out.println("  • org.tinspin.index.PointDistanceFunction → org.tinspin.index.PointDistance");
            System.out.println("  • org.tinspin.index.PointEntryDist<T>  → org.tinspin.index.Index$PointEntryKnn<T>");
            System.out.println("  • KDTree.create(dims, distFunc)        → KDTree.create(dims)");
            System.out.println();
            System.out.println("The transformation is generic and can be applied to any project.");
            return;
        }
        
        Path sourceDir = Paths.get(args[0]).toAbsolutePath();
        System.out.println("Transforming: " + sourceDir);
        
        Transformer transformer = new Transformer();
        int fileCount = transformer.transformDirectory(sourceDir);
        
        System.out.println("\nTransformation complete!");
        System.out.println("Modified " + fileCount + " Java file(s)");
        System.out.println("Total changes applied: " + transformer.getChangeCount());
    }
    
    static class Transformer {
        private int changeCount = 0;
        
        // Core transformation rules - generic and reusable
        private final List<Transformation> transformations = Arrays.asList(
            // Import declarations
            new Transformation(
                "import\\s+org\\.tinspin\\.index\\.PointIndex\\s*;",
                "import org.tinspin.index.PointMap;",
                "PointIndex import → PointMap"
            ),
            new Transformation(
                "import\\s+org\\.tinspin\\.index\\.PointDistanceFunction\\s*;", 
                "import org.tinspin.index.PointDistance;",
                "PointDistanceFunction import → PointDistance"
            ),
            new Transformation(
                "import\\s+org\\.tinspin\\.index\\.PointEntryDist\\s*;",
                "import org.tinspin.index.Index.PointEntryKnn;",
                "PointEntryDist import → Index.PointEntryKnn"
            ),
            
            // Type references with generics
            new Transformation(
                "\\bPointIndex\\s*<([^>]+)>",
                "PointMap<$1>",
                "PointIndex<T> type → PointMap<T>"
            ),
            new Transformation(
                "\\bPointDistanceFunction\\b",
                "PointDistance",
                "PointDistanceFunction type → PointDistance"
            ),
            new Transformation(
                "\\bPointEntryDist\\s*<([^>]+)>",
                "Index.PointEntryKnn<$1>",
                "PointEntryDist<T> type → Index.PointEntryKnn<T>"
            ),
            
            // Method invocations - KDTree.create() with distance function
            new Transformation(
                "KDTree\\.create\\(\\s*(\\d+)\\s*,\\s*[^)]*\\)",
                "KDTree.create($1)",
                "KDTree.create(dims, distFunc) → KDTree.create(dims)"
            ),
            
            // Simple type references (without generics)
            new Transformation(
                "\\bPointIndex\\b(?!\\s*<)",
                "PointMap",
                "PointIndex (non-generic) → PointMap"
            ),
new Transformation(
                "\\bPointEntryDist\\b(?!\\s*<)",
                "Index.PointEntryKnn",
                "PointEntryDist (non-generic) → Index.PointEntryKnn"
            ),
            
            // Method name case change: query1NN -> query1nn
            new Transformation(
                "\\bquery1NN\\b",
                "query1nn",
                "query1NN method → query1nn (case change)"
            )
        );
        
        public int transformDirectory(Path directory) throws IOException {
            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                throw new IOException("Directory not found: " + directory);
            }
            
            List<Path> javaFiles = new ArrayList<>();
            Files.walk(directory)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(javaFiles::add);
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path file : javaFiles) {
                if (transformFile(file)) {
                    modifiedFiles++;
                }
            }
            
            return modifiedFiles;
        }
        
        private boolean transformFile(Path file) throws IOException {
            String content = Files.readString(file);
            String original = content;
            
            for (Transformation t : transformations) {
                Matcher matcher = t.pattern.matcher(content);
                if (matcher.find()) {
                    content = matcher.replaceAll(t.replacement);
                    int changes = countMatches(t.pattern, original);
                    changeCount += changes;
                    System.out.println("  " + file.getFileName() + ": " + t.description + " (" + changes + "x)");
                }
            }
            
            if (!content.equals(original)) {
                Files.writeString(file, content);
                return true;
            }
            
            return false;
        }
        
        private int countMatches(Pattern pattern, String text) {
            int count = 0;
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                count++;
            }
            return count;
        }
        
        public int getChangeCount() {
            return changeCount;
        }
    }
    
    static class Transformation {
        final Pattern pattern;
        final String replacement;
        final String description;
        
        Transformation(String regex, String replacement, String description) {
            this.pattern = Pattern.compile(regex);
            this.replacement = replacement;
            this.description = description;
        }
    }
}