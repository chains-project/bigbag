package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.PrettyPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A generic transformation rule for handling breaking API changes in dependencies.
 * This transformation handles cases where a class is removed or significantly changed
 * in a new version of a dependency.
 * 
 * Configuration: Modify the constants below for your specific breaking change.
 */
public class Main {
    
    // ========== CONFIGURATION ==========
    
    /**
     * The fully qualified name of the old class that was removed or changed.
     */
    private static final String OLD_CLASS = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    
    /**
     * The simple name of the old class (extracted from fully qualified name).
     */
    private static final String OLD_CLASS_SIMPLE_NAME = 
        OLD_CLASS.substring(OLD_CLASS.lastIndexOf('.') + 1);
    
    /**
     * The fully qualified name of the new replacement class (if any).
     * Set to null if the class was removed without direct replacement.
     */
    private static final String NEW_CLASS = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    
    /**
     * How to handle instantiations of the old class.
     * Options:
     * - "THROW": Replace with code that throws an exception
     * - "NULL": Replace with null (may cause NPE)
     * - "REMOVE": Remove the expression (may break compilation)
     * - "REPLACE_WITH_NEW": Try to replace with new class (may not work due to constructor differences)
     */
    private static final String INSTANTIATION_HANDLING = "THROW";
    
    /**
     * Custom message for exception or comment.
     */
    private static final String ERROR_MESSAGE = 
        OLD_CLASS_SIMPLE_NAME + " has been removed in maven-dependency-tree 3.2.0. " +
        "Use DependencyGraphBuilder injection or provide a DefaultDependencyGraphBuilder instance.";
    
    // ========== MAIN ==========
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println();
            System.err.println("This transformation handles: " + OLD_CLASS);
            if (NEW_CLASS != null) {
                System.err.println("Replacement: " + NEW_CLASS);
            } else {
                System.err.println("No direct replacement available");
            }
            System.err.println("Instantiation handling: " + INSTANTIATION_HANDLING);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation target: " + OLD_CLASS);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int filesModified = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    filesModified++;
                }
            }
            
            System.out.println("Modified " + filesModified + " files");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        boolean modified = false;
        
        // Check for imports of the old class
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration importDecl = imports.get(i);
            if (importDecl.getNameAsString().equals(OLD_CLASS)) {
                cu.remove(importDecl);
                modified = true;
                System.out.println("Removed import: " + importDecl.getNameAsString() + " from " + javaFile);
            }
        }
        
        // If there's a new class, add import for it
        if (NEW_CLASS != null && !NEW_CLASS.isEmpty()) {
            boolean hasNewImport = imports.stream()
                .anyMatch(imp -> imp.getNameAsString().equals(NEW_CLASS));
            if (!hasNewImport) {
                // Check if we actually use the new class
                // For now, we don't add it automatically
            }
        }
        
        // Transform instantiations
        BreakingChangeTransformer transformer = new BreakingChangeTransformer();
        cu.accept(transformer, null);
        if (transformer.wasModified()) {
            modified = true;
        }
        
        if (modified) {
            PrettyPrinter printer = new PrettyPrinter();
            String newContent = printer.print(cu);
            
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Updated: " + javaFile);
        }
        
        return modified;
    }
    
    private static class BreakingChangeTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public ObjectCreationExpr visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is an instantiation of the old class
            if (n.getType().asString().equals(OLD_CLASS_SIMPLE_NAME)) {
                System.out.println("Found " + OLD_CLASS_SIMPLE_NAME + " instantiation");
                modified = true;
                
                // Check if this is inside Optional.orElse()
                if (isInsideOptionalOrElse(n)) {
                    System.out.println("  Inside Optional.orElse(), replacing with orElseThrow()");
                    replaceOptionalOrElseWithThrow(n);
                } else {
                    switch (INSTANTIATION_HANDLING) {
                        case "THROW":
                            // Replace with a method call that throws
                            replaceWithExceptionThrow(n);
                            break;
                        case "NULL":
                            // Replace with null
                            n.replace(new NullLiteralExpr());
                            break;
                        case "REMOVE":
                            // Can't remove expression from parent directly
                            // Just leave it and it will fail to compile
                            System.err.println("Warning: Cannot remove object creation expression from context");
                            break;
                        case "REPLACE_WITH_NEW":
                            if (NEW_CLASS != null) {
                                replaceWithNewClass(n);
                            } else {
                                System.err.println("Warning: No new class specified for replacement");
                            }
                            break;
                        default:
                            System.err.println("Warning: Unknown instantiation handling: " + INSTANTIATION_HANDLING);
                            break;
                    }
                }
            }
            return n;
        }
        
        private boolean isInsideOptionalOrElse(ObjectCreationExpr n) {
            // Check if parent is a method call with name "orElse"
            if (n.getParentNode().isPresent()) {
                Object parent = n.getParentNode().get();
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) parent;
                    if (methodCall.getNameAsString().equals("orElse")) {
                        // Check if this is the argument to orElse()
                        if (methodCall.getArguments().contains(n)) {
                            // Check if the method call is on Optional
                            if (methodCall.getScope().isPresent()) {
                                Expression scope = methodCall.getScope().get();
                                // Could be Optional.ofNullable(...) or similar
                                // For now, assume it's Optional
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
        
        private void replaceOptionalOrElseWithThrow(ObjectCreationExpr n) {
            // Get the parent MethodCallExpr (the orElse() call)
            if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof MethodCallExpr) {
                MethodCallExpr orElseCall = (MethodCallExpr) n.getParentNode().get();
                
                // Create a lambda expression for orElseThrow: () -> new IllegalArgumentException(...)
                // First create the exception creation expression
                ObjectCreationExpr exceptionCreation = new ObjectCreationExpr();
                exceptionCreation.setType("IllegalArgumentException");
                exceptionCreation.addArgument(new StringLiteralExpr(ERROR_MESSAGE));
                
                // Create expression statement for lambda body
                com.github.javaparser.ast.stmt.ExpressionStmt lambdaBody = 
                    new com.github.javaparser.ast.stmt.ExpressionStmt(exceptionCreation);
                
                // Create lambda
                LambdaExpr lambda = new LambdaExpr();
                lambda.setEnclosingParameters(true);
                lambda.setBody(lambdaBody);
                
                // Create new method call: orElseThrow(() -> new IllegalArgumentException(...))
                MethodCallExpr orElseThrowCall = new MethodCallExpr(
                    orElseCall.getScope().orElse(null),
                    "orElseThrow"
                );
                orElseThrowCall.addArgument(lambda);
                
                // Replace the orElse() call with orElseThrow()
                orElseCall.replace(orElseThrowCall);
            }
        }
        
        private void replaceWithExceptionThrow(ObjectCreationExpr n) {
            // Create a method call: throwRemovedClassException()
            MethodCallExpr methodCall = new MethodCallExpr(
                null,
                "throwRemovedClassException"
            );
            
            // Add the error message as argument
            methodCall.addArgument(new StringLiteralExpr(ERROR_MESSAGE));
            
            // Replace the object creation with the method call
            n.replace(methodCall);
        }
        
        private void replaceWithNewClass(ObjectCreationExpr n) {
            // Extract simple name from NEW_CLASS
            String newClassName = NEW_CLASS.substring(NEW_CLASS.lastIndexOf('.') + 1);
            
            // Create new object creation with same constructor arguments
            ObjectCreationExpr newExpr = new ObjectCreationExpr();
            newExpr.setType(newClassName);
            
            // Copy arguments if any
            if (n.getArguments() != null) {
                newExpr.setArguments(n.getArguments());
            }
            
            // Note: This may not work if constructors have different signatures!
            System.out.println("Warning: Attempting to replace " + OLD_CLASS_SIMPLE_NAME + 
                             " with " + newClassName + " - constructor signatures may differ");
            
            n.replace(newExpr);
        }
        
        @Override
        public MethodCallExpr visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Also check for static method calls on the old class
            // This is a more complex case - would need to check scope
            
            return n;
        }
    }
    
    /**
     * Helper method that would be added to client code (or suggested to be added).
     * This is shown as an example of what the transformed code expects.
     */
    private static void throwRemovedClassException(String message) {
        throw new IllegalStateException(message);
    }
}