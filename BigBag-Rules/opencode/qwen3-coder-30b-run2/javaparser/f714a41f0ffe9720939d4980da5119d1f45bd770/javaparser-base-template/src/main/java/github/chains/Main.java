package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Generic transformation rule for fixing pubnub-gson 6.3.2 breaking changes
 * This transformation handles:
 * 1. PNConfiguration constructor changes (no-arg constructor replaced with parameterized constructors)
 * 2. Missing SubscribeCallback methods (file, user, space, membership)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory recursively
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformations
            new PNConfigurationFixer().visit(cu, null);
            new SubscribeCallbackFixer().visit(cu, null);
            
            // Write back the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                cu.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {}, null);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Fix for PNConfiguration constructor changes - replaces no-arg constructor with
     * a parameterized constructor that takes a UUID string
     */
    private static class PNConfigurationFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Find the constructor call to PNConfiguration with no arguments
            if (n.getType().asString().equals("PNConfiguration")) {
                // Replace no-arg constructor with one that takes a UUID string
                if (n.getArguments().isEmpty()) {
                    // Add a UUID argument - just use a literal string for simplicity
                    n.getArguments().add(new StringLiteralExpr("default-uuid"));
                }
            }
        }
    }
    
    /**
     * Fix for SubscribeCallback missing methods
     */
    private static class SubscribeCallbackFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Check if this class extends SubscribeCallback or implements it
            Optional<ClassOrInterfaceType> subscribeCallbackExtends = n.getExtendedTypes().stream()
                .filter(type -> type.getNameAsString().contains("SubscribeCallback"))
                .findFirst();
                
            if (subscribeCallbackExtends.isPresent()) {
                // Add missing methods
                addMissingMethods(n);
            }
        }
        
        private void addMissingMethods(ClassOrInterfaceDeclaration clazz) {
            // Check if missing methods exist
            boolean hasFileMethod = clazz.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("file"));
            boolean hasUserMethod = clazz.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("user"));
            boolean hasSpaceMethod = clazz.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("space"));
            boolean hasMembershipMethod = clazz.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("membership"));
                
            if (!hasFileMethod) {
                addMethod(clazz, "file", "com.pubnub.api.PubNub", "com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult");
            }
            if (!hasUserMethod) {
                addMethod(clazz, "user", "com.pubnub.api.PubNub", "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult");
            }
            if (!hasSpaceMethod) {
                addMethod(clazz, "space", "com.pubnub.api.PubNub", "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult");
            }
            if (!hasMembershipMethod) {
                addMethod(clazz, "membership", "com.pubnub.api.PubNub", "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult");
            }
        }
        
        private void addMethod(ClassOrInterfaceDeclaration clazz, String methodName, String param1Type, String param2Type) {
            MethodDeclaration method = new MethodDeclaration();
            method.addModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
            method.setName(methodName);
            method.setType("void");
            
            // Add parameters
            method.addParameter(param1Type, "pubnub");
            method.addParameter(param2Type, "result");
            
            // Add empty body
            BlockStmt body = new BlockStmt();
            method.setBody(body);
            
            clazz.addMember(method);
        }
    }
}