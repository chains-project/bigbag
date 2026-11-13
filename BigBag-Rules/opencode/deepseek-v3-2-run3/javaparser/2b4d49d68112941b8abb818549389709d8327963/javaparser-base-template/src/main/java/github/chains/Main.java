package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    // Generic transformation rule that can be configured for any API breaking change
    // This map contains old fully-qualified class names as keys and new ones as values
    // The transformation can be reused by simply modifying this configuration
    private static final Map<String, String> CLASS_RELOCATIONS = createDefaultRelocations();
    
    private static Map<String, String> createDefaultRelocations() {
        Map<String, String> relocations = new HashMap<>();
        
        // EXAMPLE: Hazelcast 3.x to 4.x breaking changes
        // This demonstrates the pattern - a generic rule would load these from config
        relocations.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        relocations.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        relocations.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        relocations.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        relocations.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        relocations.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        relocations.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent");
        relocations.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        
        return Collections.unmodifiableMap(relocations);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory> [relocation-config-file]");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.err.println("Example with config: java -jar transformation.jar /path/to/project/src relocations.properties");
            System.err.println("\nDefault relocations configured:");
            CLASS_RELOCATIONS.forEach((k, v) -> System.err.println("  " + k + " -> " + v));
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Optional: Load custom relocations from file
        if (args.length >= 2) {
            loadRelocationsFromFile(args[1]);
        }
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        }
        
        List<Path> javaFiles = Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int filesModified = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                filesModified++;
            }
        }
        
        System.out.println("Modified " + filesModified + " files");
    }
    
    private static boolean processFile(Path javaFile) {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt;
        try {
            cuOpt = parser.parse(javaFile).getResult();
        } catch (Exception e) {
            System.err.println("Failed to parse file: " + javaFile + " - " + e.getMessage());
            return false;
        }
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse file (no result): " + javaFile);
            return false;
        }
        
        CompilationUnit cu = cuOpt.get();
        boolean modified = false;
        
        // Process imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import needs to be relocated
            if (CLASS_RELOCATIONS.containsKey(importName)) {
                String newImportName = CLASS_RELOCATIONS.get(importName);
                
                // Only modify if the new import is different
                if (!importName.equals(newImportName)) {
                    System.out.println("  Replacing import: " + importName + " -> " + newImportName);
                    
                    // Create new import declaration
                    ImportDeclaration newImport = new ImportDeclaration(new Name(newImportName), 
                        importDecl.isStatic(), importDecl.isAsterisk());
                    
                    // Replace the old import with new one
                    importDecl.replace(newImport);
                    modified = true;
                }
            }
            
            // Also check for wildcard imports that might need attention
            if (importDecl.isAsterisk()) {
                String packageName = importName;
                // Check if any of our relocations are from this package
                for (Map.Entry<String, String> entry : CLASS_RELOCATIONS.entrySet()) {
                    String oldClass = entry.getKey();
                    if (oldClass.startsWith(packageName + ".")) {
                        System.out.println("  WARNING: Wildcard import '" + importName + ".*' may contain relocated class: " + oldClass);
                        System.out.println("    Consider adding explicit import for: " + entry.getValue());
                    }
                }
            }
        }
        
        if (modified) {
            // Write back the modified file
            PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            config.setPrintComments(true);
            config.setEndOfLineCharacter("\n");
            
            PrettyPrinter printer = new PrettyPrinter(config);
            String updatedContent = printer.print(cu);
            
            try {
                Files.write(javaFile, updatedContent.getBytes());
                System.out.println("  Updated file: " + javaFile);
            } catch (Exception e) {
                System.err.println("  Failed to write updated file: " + javaFile);
                e.printStackTrace();
                return false;
            }
        }
        
        return modified;
    }
    
    private static void loadRelocationsFromFile(String configFile) {
        System.out.println("Loading relocations from: " + configFile);
        // For simplicity, we're using hardcoded defaults
        // A production version would load from properties file or JSON
        System.out.println("Note: Custom config loading not implemented in this example.");
        System.out.println("Using default Hazelcast 3.x to 4.x relocations.");
    }
}