package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generic transformation for package relocation breaking changes.
 * 
 * This tool fixes compilation errors when classes/enums move between packages.
 * 
 * Usage: java Main <sourceDirectory> <outputDirectory>
 * 
 * Example breaking change pattern:
 * - Old: import eu.europa.esig.dss.pades.CertificationPermission;
 * - New: import eu.europa.esig.dss.enumerations.CertificationPermission;
 * 
 * The transformation handles:
 * 1. Single type imports
 * 2. Static imports  
 * 3. Fully-qualified type references in code
 */
public class Main {
    
    // Define relocation mappings: oldFQN -> newFQN
    private static final List<Relocation> RELOCATIONS = new ArrayList<>();
    
    static {
        // Add relocation rules here
        // Format: new Relocation("old.package.Class", "new.package.Class")
        RELOCATIONS.add(new Relocation(
            "eu.europa.esig.dss.pades.CertificationPermission",
            "eu.europa.esig.dss.enumerations.CertificationPermission"
        ));
        // Add more relocations as needed
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDirectory> <outputDirectory>");
            System.err.println("Example: java Main /path/to/src /path/to/transformed");
            System.err.println("\nTransforms package relocation breaking changes.");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        
        if (!Files.exists(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println("Relocation rules: " + RELOCATIONS.size());
        
        // Create output directory
        Files.createDirectories(outputDir);
        
        // Process all Java files
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalChanges = 0;
        
        for (Path javaFile : javaFiles) {
            int changes = processFile(javaFile, sourceDir, outputDir);
            totalChanges += changes;
            if (changes > 0) {
                System.out.println("  " + javaFile + ": " + changes + " change(s)");
            }
        }
        
        System.out.println("\nTotal changes applied: " + totalChanges);
        System.out.println("Transformation complete.");
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(files::add);
        return files;
    }
    
    private static int processFile(Path inputFile, Path sourceDir, Path outputDir) throws IOException {
        String content = Files.readString(inputFile);
        String original = content;
        
        int changes = 0;
        
        for (Relocation reloc : RELOCATIONS) {
            // Replace import statements
            String oldImport = "import " + reloc.oldFqn + ";";
            String newImport = "import " + reloc.newFqn + ";";
            
            if (content.contains(oldImport)) {
                content = content.replace(oldImport, newImport);
                changes++;
            }
            
            // Replace static imports
            String oldStaticImport = "import static " + reloc.oldFqn;
            String newStaticImport = "import static " + reloc.newFqn;
            
            if (content.contains(oldStaticImport)) {
                content = content.replace(oldStaticImport, newStaticImport);
                changes++;
            }
            
            // Replace fully-qualified references in code
            // This is a simple regex approach - for complex cases, use Spoon
            String oldFqnRegex = Pattern.quote(reloc.oldFqn);
            String newFqn = reloc.newFqn;
            
            // Count replacements
            long count = Pattern.compile(oldFqnRegex).matcher(content).results().count();
            if (count > 0) {
                content = content.replaceAll(oldFqnRegex, newFqn);
                changes += count;
            }
        }
        
        if (changes > 0) {
            // Determine output path
            Path relativePath = sourceDir.relativize(inputFile);
            Path outputFile = outputDir.resolve(relativePath);
            
            // Create parent directories
            Files.createDirectories(outputFile.getParent());
            
            // Write transformed file
            Files.writeString(outputFile, content);
        } else {
            // Copy unchanged file
            Path relativePath = sourceDir.relativize(inputFile);
            Path outputFile = outputDir.resolve(relativePath);
            Files.createDirectories(outputFile.getParent());
            Files.copy(inputFile, outputFile);
        }
        
        return changes;
    }
    
    static class Relocation {
        final String oldFqn;
        final String newFqn;
        
        Relocation(String oldFqn, String newFqn) {
            this.oldFqn = oldFqn;
            this.newFqn = newFqn;
        }
        
        @Override
        public String toString() {
            return oldFqn + " -> " + newFqn;
        }
    }
}