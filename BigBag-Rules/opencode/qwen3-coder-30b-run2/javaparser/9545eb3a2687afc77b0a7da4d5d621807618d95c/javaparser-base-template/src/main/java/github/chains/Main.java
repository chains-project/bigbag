package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // Parse command line arguments
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        // Define the breaking changes mapping
        Map<String, String> breakingChanges = new HashMap<>();
        breakingChanges.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        breakingChanges.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        breakingChanges.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        breakingChanges.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        breakingChanges.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent");
        breakingChanges.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        breakingChanges.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        breakingChanges.put("com.hazelcast.monitor", "com.hazelcast.internal.monitor");
        breakingChanges.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.MaxSizeConfig");
        
        // Process all Java files in the source directory recursively
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(file -> processJavaFile(file, breakingChanges));
    }
    
    private static void processJavaFile(Path filePath, Map<String, String> breakingChanges) {
        try {
            // Parse the Java file
            FileInputStream in = new FileInputStream(filePath.toFile());
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Apply transformations
            boolean modified = false;
            
            // Transform import statements
            modified |= transformImports(cu, breakingChanges);
            
            // If any changes were made, save the file
            if (modified) {
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Processed: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean transformImports(CompilationUnit cu, Map<String, String> breakingChanges) {
        boolean modified = false;
        
        // Get all existing imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration imp : imports) {
            String importName = imp.getNameAsString();
            
            // Check if this import needs to be updated
            if (breakingChanges.containsKey(importName)) {
                String newImport = breakingChanges.get(importName);
                imp.setName(newImport);
                modified = true;
            }
        }
        
        return modified;
    }
}
