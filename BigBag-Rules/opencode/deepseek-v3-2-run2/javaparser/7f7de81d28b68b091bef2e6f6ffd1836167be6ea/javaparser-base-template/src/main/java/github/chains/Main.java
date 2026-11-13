package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String DEPENDENCY_CLASS_NAME = "org.snmp4j.agent.DefaultMOServer";
    private static final String DEPENDENCY_METHOD_NAME = "getRegistry";
    private static final String OLD_TYPE_PATTERN = "ManagedObject";
    private static final String NEW_TYPE_PATTERN = "ManagedObject<?>";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully processed " + javaFiles.size() + " files, modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        boolean modified = false;
        
        // Visitor to find and fix getRegistry() method calls
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                // Check if this is a getRegistry() call
                if (n.getNameAsString().equals(DEPENDENCY_METHOD_NAME)) {
                    // Look for parent variable declarations or assignments
                    Node parent = n.getParentNode().orElse(null);
                    
                    // This visitor will handle the type transformation
                    // The actual type transformation happens at the declaration level
                    // We need to find the surrounding context where the type is declared
                    
                    // For now, we'll transform the entire file by replacing type patterns
                    // A more sophisticated approach would traverse the AST and find
                    // variable declarations that use the result of getRegistry()
                }
                return super.visit(n, arg);
            }
        }, null);
        
        // Apply type pattern replacement across the entire file
        String originalContent = cu.toString();
        String transformedContent = transformTypePatterns(originalContent);
        
        if (!originalContent.equals(transformedContent)) {
            Files.writeString(filePath, transformedContent);
            System.out.println("Modified: " + filePath);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * Transforms type patterns in the source code to handle the breaking API change.
     * This method replaces occurrences of the old type pattern with the new type pattern
     * in variable declarations, method parameters, and return types.
     */
    private static String transformTypePatterns(String content) {
        // Pattern to match SortedMap<MOScope, ManagedObject> 
        // and replace with SortedMap<MOScope, ManagedObject<?>>
        
        // This handles the specific breaking change where getRegistry() now returns
        // SortedMap<MOScope, ManagedObject<?>> instead of SortedMap<MOScope, ManagedObject>
        
        String transformed = content;
        
        // Replace the specific pattern that causes compilation errors
        // This pattern matches variable declarations that assign getRegistry() result
        transformed = transformed.replaceAll(
            "SortedMap<MOScope,\\s*ManagedObject>",
            "SortedMap<MOScope, ManagedObject<?>>"
        );
        
        // Also handle cases with different spacing
        transformed = transformed.replaceAll(
            "SortedMap<MOScope,\\s*ManagedObject\\s*>",
            "SortedMap<MOScope, ManagedObject<?>>"
        );
        
        return transformed;
    }
}