package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for replacing removed constant classes with literal values.
 * 
 * This transformation handles the pattern where a class containing static integer
 * constants is removed, and its constants need to be replaced with literal values.
 * 
 * Pattern: ClassName.CONSTANT_NAME -> literalValue
 * Example: com.jcabi.aspects.Tv.FIVE -> 5
 */
public class Main {
    
    // Configuration: Map of fully qualified class names to their constant mappings
    private static final Map<String, Map<String, Integer>> CLASS_CONSTANT_MAPPINGS = new HashMap<>();
    
    static {
        // Example configuration for com.jcabi.aspects.Tv
        Map<String, Integer> tvConstants = new HashMap<>();
        tvConstants.put("FIVE", 5);
        tvConstants.put("HUNDRED", 100);
        tvConstants.put("THOUSAND", 1000);
        tvConstants.put("MILLION", 1000000);
        tvConstants.put("BILLION", 1000000000);
        CLASS_CONSTANT_MAPPINGS.put("com.jcabi.aspects.Tv", tvConstants);
        
        // Add more class mappings here as needed
        // Format: CLASS_CONSTANT_MAPPINGS.put("fully.qualified.ClassName", constantMap);
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms removed constant classes to literal values");
            System.err.println("");
            System.err.println("Supported transformations:");
            for (String className : CLASS_CONSTANT_MAPPINGS.keySet()) {
                System.err.println("  - " + className + " constants -> integer literals");
            }
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Walk through all Java files recursively
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        System.out.println("Looking for constants from: " + CLASS_CONSTANT_MAPPINGS.keySet());
        
        int modifiedCount = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedCount++;
            }
        }
        
        System.out.println("Modified " + modifiedCount + " files");
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser javaParser = new JavaParser();
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
                () -> new IOException("Failed to parse " + javaFile)
            );
            
            // Create visitor to transform constants
            ConstantReplacementVisitor visitor = new ConstantReplacementVisitor(cu);
            cu.accept(visitor, null);
            
            // Remove imports of removed classes
            boolean removedImport = false;
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration imp : cu.getImports()) {
                String importName = imp.getNameAsString();
                
                for (String className : CLASS_CONSTANT_MAPPINGS.keySet()) {
                    // Check for regular import
                    if (importName.equals(className)) {
                        importsToRemove.add(imp);
                        removedImport = true;
                    }
                    // Check for static star import
                    if (importName.equals("static " + className + ".*")) {
                        importsToRemove.add(imp);
                        removedImport = true;
                    }
                    // Check for static specific constant import
                    if (importName.startsWith("static " + className + ".")) {
                        importsToRemove.add(imp);
                        removedImport = true;
                    }
                }
            }
            
            if (!importsToRemove.isEmpty()) {
                cu.getImports().removeAll(importsToRemove);
            }
            
            if (visitor.wasModified() || removedImport) {
                // Write back the modified file
                Files.write(javaFile, cu.toString().getBytes());
                System.out.println("Modified: " + javaFile);
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
        }
        return false;
    }
    
    private static class ConstantReplacementVisitor extends ModifierVisitor<Void> {
        private final CompilationUnit cu;
        private boolean modified = false;
        // Map of simple class names to their fully qualified names for this file
        private final Map<String, String> simpleToFqnMap = new HashMap<>();
        
        public ConstantReplacementVisitor(CompilationUnit cu) {
            this.cu = cu;
            
            // Build mapping of simple names to FQN for classes we're transforming
            for (ImportDeclaration imp : cu.getImports()) {
                String importName = imp.getNameAsString();
                for (String fqn : CLASS_CONSTANT_MAPPINGS.keySet()) {
                    if (importName.equals(fqn)) {
                        // Extract simple name from FQN
                        String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
                        simpleToFqnMap.put(simpleName, fqn);
                    }
                }
            }
        }
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is ClassName.CONSTANT pattern
            if (n.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) n.getScope();
                String scopeName = scope.getNameAsString();
                String fieldName = n.getNameAsString();
                
                // Check if scope is a class we're transforming
                if (simpleToFqnMap.containsKey(scopeName)) {
                    String fqn = simpleToFqnMap.get(scopeName);
                    Map<String, Integer> constants = CLASS_CONSTANT_MAPPINGS.get(fqn);
                    
                    if (constants != null && constants.containsKey(fieldName)) {
                        // Replace ClassName.CONSTANT with integer literal
                        IntegerLiteralExpr replacement = new IntegerLiteralExpr(
                            constants.get(fieldName).toString()
                        );
                        modified = true;
                        return replacement;
                    }
                }
            }
            
            // Also handle fully qualified names: fully.qualified.ClassName.CONSTANT
            for (String fqn : CLASS_CONSTANT_MAPPINGS.keySet()) {
                if (n.toString().startsWith(fqn + ".")) {
                    String fieldName = n.getNameAsString();
                    Map<String, Integer> constants = CLASS_CONSTANT_MAPPINGS.get(fqn);
                    
                    if (constants != null && constants.containsKey(fieldName)) {
                        IntegerLiteralExpr replacement = new IntegerLiteralExpr(
                            constants.get(fieldName).toString()
                        );
                        modified = true;
                        return replacement;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(NameExpr n, Void arg) {
            // Handle static imports: CONSTANT (import static fully.qualified.ClassName.CONSTANT)
            String name = n.getNameAsString();
            
            // Check all constant mappings for this name
            for (Map.Entry<String, Map<String, Integer>> entry : CLASS_CONSTANT_MAPPINGS.entrySet()) {
                Map<String, Integer> constants = entry.getValue();
                if (constants.containsKey(name)) {
                    // This could be a statically imported constant
                    // In a production system, we'd want to verify via symbol resolution
                    // For this example, we'll replace it
                    IntegerLiteralExpr replacement = new IntegerLiteralExpr(
                        constants.get(name).toString()
                    );
                    modified = true;
                    return replacement;
                }
            }
            
            return super.visit(n, arg);
        }
    }
}
