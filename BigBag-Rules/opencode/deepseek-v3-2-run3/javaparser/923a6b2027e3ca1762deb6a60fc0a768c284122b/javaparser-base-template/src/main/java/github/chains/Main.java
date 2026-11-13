package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Hamcrest 2.2 breaking changes.
 * 
 * BREAKING CHANGE ANALYSIS:
 * - Hamcrest 2.2 consolidates hamcrest-core and hamcrest-library into hamcrest jar
 * - hamcrest-library.jar is deprecated (contains only deprecated marker)
 * - Some method signatures may have changed between versions
 * 
 * TRANSFORMATION PATTERNS:
 * 1. Package migration: org.hamcrest.library.* -> org.hamcrest.*
 * 2. Method signature updates for deprecated/removed methods
 * 3. Static import updates
 * 
 * USAGE:
 * java Main <source-directory> [<old-version> <new-version>]
 * 
 * Example: java Main /path/to/project 1.3 2.2
 */
public class Main {
    // Configuration for Hamcrest 2.2 specific transformations
    private static final Map<String, String> PACKAGE_MAPPINGS = Map.of(
        "org.hamcrest.library", "org.hamcrest"
    );
    
    // Example method transformations (oldMethod -> newMethod)
    private static final Map<String, String> METHOD_TRANSFORMATIONS = Map.of(
        // "oldMethodName", "newMethodName"
        // Add specific method transformations here
    );
    
    // Method signature transformations (method name -> parameter transformation)
    private static final Map<String, MethodTransformation> SIGNATURE_TRANSFORMATIONS = 
        new HashMap<>();
    
    static {
        // Example: emptyIterableOf(Class) -> emptyIterable() 
        // (if emptyIterableOf was removed in 2.2)
        // SIGNATURE_TRANSFORMATIONS.put("emptyIterableOf", 
        //     new MethodTransformation("emptyIterable", 0));
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [<old-version> <new-version>]");
            System.err.println("Example: java Main /path/to/project 1.3 2.2");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        String oldVersion = args.length > 1 ? args[1] : "1.3";
        String newVersion = args.length > 2 ? args[2] : "2.2";
        
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing directory: " + sourceDir);
        System.out.println("Transforming Hamcrest from version " + oldVersion + " to " + newVersion);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalChanges = 0;
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                DependencyUpdateVisitor visitor = new DependencyUpdateVisitor();
                cu.accept(visitor, null);
                
                if (visitor.isModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Modified: " + javaFile);
                    totalChanges++;
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTotal files modified: " + totalChanges);
        System.out.println("\nTRANSFORMATION SUMMARY:");
        System.out.println("1. Package migrations applied: " + PACKAGE_MAPPINGS.size() + " patterns");
        System.out.println("2. Method transformations: " + METHOD_TRANSFORMATIONS.size() + " mappings");
        System.out.println("3. Signature transformations: " + SIGNATURE_TRANSFORMATIONS.size() + " patterns");
        System.out.println("\nNOTE: This is a generic transformation rule template.");
        System.out.println("Add specific transformation patterns based on actual API changes.");
    }
    
    /**
     * Visitor for dependency update transformations.
     * Generic pattern matching for:
     * - Package migrations
     * - Method renames
     * - Signature changes
     */
    private static class DependencyUpdateVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            MethodCallExpr visited = (MethodCallExpr) super.visit(n, arg);
            
            // Pattern 1: Check for method renames
            String methodName = visited.getNameAsString();
            if (METHOD_TRANSFORMATIONS.containsKey(methodName)) {
                String newMethodName = METHOD_TRANSFORMATIONS.get(methodName);
                visited.setName(newMethodName);
                modified = true;
                System.out.println("  -> Renamed method: " + methodName + " -> " + newMethodName);
            }
            
            // Pattern 2: Check for signature transformations
            if (SIGNATURE_TRANSFORMATIONS.containsKey(methodName)) {
                MethodTransformation transformation = SIGNATURE_TRANSFORMATIONS.get(methodName);
                if (visited.getArguments().size() == transformation.oldParamCount) {
                    visited.setName(transformation.newMethodName);
                    // Adjust arguments if needed
                    if (transformation.newParamCount < visited.getArguments().size()) {
                        // Remove excess arguments
                        NodeList newArgs = new NodeList();
                        for (int i = 0; i < transformation.newParamCount; i++) {
                            newArgs.add(visited.getArguments().get(i));
                        }
                        visited.setArguments(newArgs);
                    } else if (transformation.newParamCount > visited.getArguments().size()) {
                        // Add default arguments
                        for (int i = visited.getArguments().size(); i < transformation.newParamCount; i++) {
                            visited.getArguments().add(new StringLiteralExpr("default"));
                        }
                    }
                    modified = true;
                    System.out.println("  -> Transformed signature: " + methodName + 
                                     "(" + transformation.oldParamCount + ") -> " +
                                     transformation.newMethodName + 
                                     "(" + transformation.newParamCount + ")");
                }
            }
            
            // Pattern 3: Matchers.methodName() pattern matching
            if (visited.getScope().isPresent() && 
                visited.getScope().get().isNameExpr()) {
                NameExpr scope = visited.getScope().get().asNameExpr();
                if (scope.getNameAsString().equals("Matchers")) {
                    // Add Matchers-specific transformations here
                    // Example: if (methodName.equals("someDeprecatedMethod")) { ... }
                }
            }
            
            return visited;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            ImportDeclaration visited = (ImportDeclaration) super.visit(n, arg);
            String importStr = visited.getNameAsString();
            
            // Apply package migrations
            for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
                String oldPackage = entry.getKey();
                String newPackage = entry.getValue();
                
                if (importStr.startsWith(oldPackage + ".")) {
                    String newImport = importStr.replace(oldPackage + ".", newPackage + ".");
                    ImportDeclaration newDecl = new ImportDeclaration(
                        newImport, 
                        visited.isStatic(), 
                        visited.isAsterisk()
                    );
                    modified = true;
                    System.out.println("  -> Package migration: " + importStr + " -> " + newImport);
                    return newDecl;
                }
            }
            
            // Specific class migrations
            // Example: if (importStr.equals("org.old.Class")) { ... }
            
            return visited;
        }
    }
    
    /**
     * Represents a method signature transformation.
     */
    private static class MethodTransformation {
        final String newMethodName;
        final int oldParamCount;
        final int newParamCount;
        
        MethodTransformation(String newMethodName, int newParamCount) {
            this.newMethodName = newMethodName;
            this.oldParamCount = -1; // Any count
            this.newParamCount = newParamCount;
        }
        
        MethodTransformation(String newMethodName, int oldParamCount, int newParamCount) {
            this.newMethodName = newMethodName;
            this.oldParamCount = oldParamCount;
            this.newParamCount = newParamCount;
        }
    }
}