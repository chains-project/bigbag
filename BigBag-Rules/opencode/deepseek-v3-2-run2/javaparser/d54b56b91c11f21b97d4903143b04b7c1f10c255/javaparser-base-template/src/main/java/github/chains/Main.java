package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    // Configuration for type replacements
    private static final Map<String, String> TYPE_REPLACEMENTS = new HashMap<>();
    // Configuration for method removals
    private static final Set<String> METHODS_TO_REMOVE = new HashSet<>();
    
    static {
        // Generic configuration - these would be parameters in a real tool
        TYPE_REPLACEMENTS.put("com.google.cloud.pubsublite.PublishMetadata", 
                              "com.google.cloud.pubsublite.MessageMetadata");
        
        METHODS_TO_REMOVE.add("setContext");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Configurable transformations:");
            System.err.println("  Type replacements: " + TYPE_REPLACEMENTS);
            System.err.println("  Method removals: " + METHODS_TO_REMOVE);
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying transformations:");
        System.out.println("  Type replacements: " + TYPE_REPLACEMENTS);
        System.out.println("  Method removals: " + METHODS_TO_REMOVE);
        
        try {
            Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::transformFile);
                
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Apply type replacement transformation
            TypeReplacementTransformer typeTransformer = new TypeReplacementTransformer(TYPE_REPLACEMENTS);
            cu.accept(typeTransformer, null);
            modified = typeTransformer.isModified();
            
            // Apply method removal transformation
            MethodRemovalTransformer methodTransformer = new MethodRemovalTransformer(METHODS_TO_REMOVE);
            cu.accept(methodTransformer, null);
            modified = modified || methodTransformer.isModified();
            
            if (modified) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Modified: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Generic transformer for type replacements
    static class TypeReplacementTransformer extends ModifierVisitor<Void> {
        private final Map<String, String> typeReplacements;
        private final Map<String, String> simpleNameReplacements;
        private boolean modified = false;
        
        public TypeReplacementTransformer(Map<String, String> typeReplacements) {
            this.typeReplacements = typeReplacements;
            this.simpleNameReplacements = new HashMap<>();
            
            // Build map of simple name replacements
            for (Map.Entry<String, String> entry : typeReplacements.entrySet()) {
                String oldSimple = getSimpleName(entry.getKey());
                String newSimple = getSimpleName(entry.getValue());
                if (!oldSimple.equals(newSimple)) {
                    simpleNameReplacements.put(oldSimple, newSimple);
                }
            }
        }
        
        private String getSimpleName(String fullyQualifiedName) {
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
        }
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            if (typeReplacements.containsKey(importName)) {
                modified = true;
                importDecl.setName(typeReplacements.get(importName));
            }
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            if (simpleNameReplacements.containsKey(typeName)) {
                // This is a simple name replacement case
                // We should verify it's the right type by checking imports
                // For now, we'll apply the replacement
                modified = true;
                type.setName(simpleNameReplacements.get(typeName));
            }
            return (Node) super.visit(type, arg);
        }
    }
    
    // Generic transformer for method removal from builder chains
    static class MethodRemovalTransformer extends ModifierVisitor<Void> {
        private final Set<String> methodsToRemove;
        private boolean modified = false;
        
        public MethodRemovalTransformer(Set<String> methodsToRemove) {
            this.methodsToRemove = methodsToRemove;
        }
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            String methodName = methodCall.getNameAsString();
            
            if (methodsToRemove.contains(methodName)) {
                // We found a method call to remove
                // Check if it's part of a chain (parent is also a MethodCallExpr)
                if (methodCall.getParentNode().isPresent()) {
                    Object parent = methodCall.getParentNode().get();
                    
                    if (parent instanceof MethodCallExpr) {
                        // This method is being called as part of a chain
                        // e.g., builder.methodToRemove().otherMethod()
                        // We need to remove this call from the chain
                        
                        MethodCallExpr parentCall = (MethodCallExpr) parent;
                        
                        // The scope of the parent call should be the scope of methodToRemove
                        // So we replace parentCall with just the scope
                        if (methodCall.getScope().isPresent()) {
                            modified = true;
                            return (Node) methodCall.getScope().get();
                        }
                    }
                }
                
                // If not part of a chain, we should remove the entire expression
                // But that's more complex - for now we'll handle the chain case
            }
            
            // Continue visiting child nodes
            return (Node) super.visit(methodCall, arg);
        }
    }
}