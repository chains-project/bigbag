package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Generic Hazelcast dependency breaking change fixer.
 * This transformation rule fixes the breaking changes introduced in Hazelcast 4.0.5
 * where several core classes were moved from com.hazelcast.core to com.hazelcast.cluster
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp <jar-file> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(sourceDirectory);
            System.out.println("Hazelcast API fixes applied successfully to " + sourceDirectory);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Files.walk(Paths.get(directoryPath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            File file = filePath.toFile();
            FileInputStream inputStream = new FileInputStream(file);
            CompilationUnit cu = StaticJavaParser.parse(inputStream);
            inputStream.close();
            
            // Apply transformations to imports
            boolean modified = applyImportTransformations(cu);
            
            if (modified) {
                // Write back to file
                FileWriter writer = new FileWriter(file);
                writer.write(cu.toString());
                writer.close();
                System.out.println("Modified file: " + file.getPath());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath.toString() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean applyImportTransformations(CompilationUnit cu) {
        boolean modified = false;
        
        // Maps of old API to new API for Hazelcast 4.0 migration
        List<String[]> API_CHANGES = Arrays.asList(
            // Core package relocations
            new String[]{"com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster"},
            new String[]{"com.hazelcast.core.Member", "com.hazelcast.cluster.Member"},
            new String[]{"com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent"},
            new String[]{"com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent"},
            new String[]{"com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener"},
            
            // Management package relocations
            new String[]{"com.hazelcast.monitor.LocalMapStats", "com.hazelcast.management.LocalMapStats"}
        );
        
        // Process all import declarations
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            for (String[] mapping : API_CHANGES) {
                String oldApi = mapping[0];
                String newApi = mapping[1];
                
                if (importName.equals(oldApi)) {
                    importDecl.setName(newApi);
                    modified = true;
                    System.out.println("Updated import from " + oldApi + " to " + newApi);
                    break;
                }
            }
        }
        
        return modified;
    }
}