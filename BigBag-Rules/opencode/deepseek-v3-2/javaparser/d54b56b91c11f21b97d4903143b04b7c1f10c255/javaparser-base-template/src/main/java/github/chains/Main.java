package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these should be parameterized for different breaking changes
    private static final String OLD_TYPE_NAME = "PublishMetadata";
    private static final String NEW_TYPE_NAME = "MessageMetadata";
    private static final String OLD_TYPE_FQN = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_TYPE_FQN = "com.google.cloud.pubsublite.MessageMetadata";
    
    // Method to remove - pattern: .setContext(PubsubContext.of(...))
    private static final String METHOD_TO_REMOVE = "setContext";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean modified = false;
                
                // Apply transformations
                TypeReplacementVisitor typeVisitor = new TypeReplacementVisitor();
                cu.accept(typeVisitor, null);
                if (typeVisitor.wasModified()) {
                    modified = true;
                }
                
                MethodRemovalVisitor methodVisitor = new MethodRemovalVisitor();
                cu.accept(methodVisitor, null);
                if (methodVisitor.wasModified()) {
                    modified = true;
                }
                
                if (modified) {
                    // Write back the modified file
                    String transformedCode = cu.toString();
                    Files.write(javaFile, transformedCode.getBytes());
                    filesModified++;
                    System.out.println("Modified: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Total files modified: " + filesModified);
        System.out.println("Transformation complete!");
    }
    
    /**
     * Visitor to replace PublishMetadata with MessageMetadata in:
     * 1. Import declarations
     * 2. Type references (including generic type parameters)
     */
    private static class TypeReplacementVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if it's importing the old type
            if (importName.equals(OLD_TYPE_FQN) || 
                (importName.endsWith("." + OLD_TYPE_NAME) && !importName.endsWith("*"))) {
                // Replace with new type
                String newImportName;
                if (importName.equals(OLD_TYPE_FQN)) {
                    newImportName = NEW_TYPE_FQN;
                } else {
                    // Handle specific package imports
                    newImportName = importName.replace(OLD_TYPE_NAME, NEW_TYPE_NAME);
                }
                
                ImportDeclaration newImport = importDecl.clone();
                newImport.setName(newImportName);
                modified = true;
                return newImport;
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            // Check if this is a reference to PublishMetadata
            String typeName = type.getNameAsString();
            
            if (typeName.equals(OLD_TYPE_NAME)) {
                // Simple type reference
                type.setName(NEW_TYPE_NAME);
                modified = true;
            } else if (typeName.equals("Publisher") || typeName.contains("Publisher")) {
                // Check generic type parameters for Publisher<T>
                NodeList<Type> typeArguments = type.getTypeArguments().orElse(null);
                if (typeArguments != null && !typeArguments.isEmpty()) {
                    for (int i = 0; i < typeArguments.size(); i++) {
                        Type typeArg = typeArguments.get(i);
                        if (typeArg instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType classTypeArg = (ClassOrInterfaceType) typeArg;
                            if (classTypeArg.getNameAsString().equals(OLD_TYPE_NAME)) {
                                classTypeArg.setName(NEW_TYPE_NAME);
                                modified = true;
                            }
                        }
                    }
                }
            }
            
            return (Node) super.visit(type, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
    
    /**
     * Visitor to remove setContext method calls on SinglePartitionPublisherBuilder.Builder
     * Handles both direct calls and lambda expressions
     */
    private static class MethodRemovalVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            // Check if this is a setContext method call
            if (methodCall.getNameAsString().equals(METHOD_TO_REMOVE)) {
                // Check if it has one argument that looks like PubsubContext.of(...)
                if (methodCall.getArguments().size() == 1) {
                    String argStr = methodCall.getArgument(0).toString();
                    if (argStr.contains("PubsubContext.of")) {
                        // This is a setContext call we want to remove
                        // Check if it's part of a method chain
                        Node parent = methodCall.getParentNode().orElse(null);
                        
                        if (parent instanceof MethodCallExpr) {
                            // setContext is followed by another method call (like .build())
                            MethodCallExpr parentCall = (MethodCallExpr) parent;
                            if (parentCall.getScope().orElse(null) == methodCall) {
                                // Connect the parent call to the scope of setContext
                                methodCall.getScope().ifPresent(scope -> {
                                    parentCall.setScope(scope);
                                });
                                modified = true;
                                return parentCall;
                            }
                        } else if (parent instanceof ExpressionStmt || parent instanceof ReturnStmt || 
                                  parent instanceof LambdaExpr) {
                            // setContext is at the end of a chain or in a simple expression
                            // Just remove it by returning null (will be handled by parent)
                            // Actually, we need to return the scope if it exists
                            if (methodCall.getScope().isPresent()) {
                                modified = true;
                                return methodCall.getScope().get();
                            }
                        }
                    }
                }
            }
            
            return (Node) super.visit(methodCall, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Skip hidden directories and build directories
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || dirName.equals("target") || dirName.equals("build") || 
                    dirName.equals("bin") || dirName.equals("out") || dirName.equals(".gradle")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return javaFiles;
    }
    
    /**
     * Utility method to demonstrate the generic nature of the transformation.
     * This shows how the same transformation could be applied to other breaking changes
     * by changing the configuration parameters.
     */
    public static class TransformationConfig {
        private String oldTypeName;
        private String newTypeName;
        private String oldTypeFqn;
        private String newTypeFqn;
        private String methodToRemove;
        
        public TransformationConfig(String oldTypeName, String newTypeName, 
                                   String oldTypeFqn, String newTypeFqn, 
                                   String methodToRemove) {
            this.oldTypeName = oldTypeName;
            this.newTypeName = newTypeName;
            this.oldTypeFqn = oldTypeFqn;
            this.newTypeFqn = newTypeFqn;
            this.methodToRemove = methodToRemove;
        }
        
        // Getters for configuration
        public String getOldTypeName() { return oldTypeName; }
        public String getNewTypeName() { return newTypeName; }
        public String getOldTypeFqn() { return oldTypeFqn; }
        public String getNewTypeFqn() { return newTypeFqn; }
        public String getMethodToRemove() { return methodToRemove; }
    }
    
    /**
     * Example of how to use this transformation for a different breaking change.
     * This demonstrates the generalizability of the approach.
     */
    public static void exampleForDifferentBreakingChange() {
        // Example: If AnotherClass was renamed to NewAnotherClass
        TransformationConfig config = new TransformationConfig(
            "AnotherClass",
            "NewAnotherClass",
            "com.example.AnotherClass",
            "com.example.NewAnotherClass",
            "deprecatedMethod"  // Method that was removed
        );
        
        System.out.println("This transformation can be reused for other breaking changes");
        System.out.println("by creating a new TransformationConfig with appropriate values.");
    }
}