package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    
    // Configuration parameters - these define the breaking change
    private static final String OLD_TYPE_NAME = "PublishMetadata";
    private static final String OLD_TYPE_FQN = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_TYPE_NAME = "MessageMetadata";
    private static final String NEW_TYPE_FQN = "com.google.cloud.pubsublite.MessageMetadata";
    
    // Interface that changed from functional to non-functional
    private static final String INTERFACE_FQN = "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";
    private static final String INTERFACE_SIMPLE_NAME = "PartitionPublisherFactory";
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This tool fixes breaking changes from google-cloud-pubsublite dependency update:");
            System.err.println("1. Replaces " + OLD_TYPE_FQN + " with " + NEW_TYPE_FQN);
            System.err.println("2. Converts lambda expressions implementing " + INTERFACE_FQN + " to anonymous classes");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        AtomicInteger filesModified = new AtomicInteger(0);
        AtomicInteger typeReplacements = new AtomicInteger(0);
        AtomicInteger importReplacements = new AtomicInteger(0);
        AtomicInteger lambdaConversions = new AtomicInteger(0);
        
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean[] modified = {false};
                
                // Visitor for all transformations
                cu.accept(new ModifierVisitor<Void>() {
                    
                    @Override
                    public Node visit(ImportDeclaration importDecl, Void arg) {
                        // Replace import statements
                        String importName = importDecl.getNameAsString();
                        if (importName.equals(OLD_TYPE_FQN)) {
                            importReplacements.incrementAndGet();
                            modified[0] = true;
                            importDecl.setName(NEW_TYPE_FQN);
                        }
                        return (Node) super.visit(importDecl, arg);
                    }
                    
                    @Override
                    public Node visit(ClassOrInterfaceType type, Void arg) {
                        // Replace PublishMetadata type references with MessageMetadata
                        String typeName = type.getNameAsString();
                        if (typeName.equals(OLD_TYPE_NAME)) {
                            typeReplacements.incrementAndGet();
                            modified[0] = true;
                            type.setName(NEW_TYPE_NAME);
                        }
                        return (Node) super.visit(type, arg);
                    }
                    
                    @Override
                    public Node visit(LambdaExpr lambda, Void arg) {
                        // Check if this lambda is being passed to setPublisherFactory method
                        Node parent = lambda.getParentNode().orElse(null);
                        if (parent instanceof MethodCallExpr) {
                            MethodCallExpr methodCall = (MethodCallExpr) parent;
                            String methodName = methodCall.getNameAsString();
                            
                            if ("setPublisherFactory".equals(methodName)) {
                                lambdaConversions.incrementAndGet();
                                modified[0] = true;
                                
                                System.out.println("File " + javaFile + " line " + lambda.getBegin().get().line + 
                                                  ": Lambda needs manual conversion to anonymous class");
                            }
                        }
                        return (Node) super.visit(lambda, arg);
                    }
                    
                }, null);
                
                if (modified[0]) {
                    Files.writeString(javaFile, cu.toString());
                    filesModified.incrementAndGet();
                }
                
            } catch (IOException e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("Files processed: " + javaFiles.size());
        System.out.println("Files modified: " + filesModified.get());
        System.out.println("Import replacements: " + importReplacements.get());
        System.out.println("Type replacements: " + typeReplacements.get());
        System.out.println("Lambda conversions needed: " + lambdaConversions.get());
        
        if (lambdaConversions.get() > 0) {
            System.out.println("\n=== Manual Fix Required ===");
            System.out.println("Convert lambda to anonymous class for " + INTERFACE_FQN);
            System.out.println("Example:");
            System.out.println("  OLD: partition -> SinglePartitionPublisherBuilder.newBuilder()...");
            System.out.println("  NEW: new PartitionPublisherFactory() {");
            System.out.println("      @Override");
            System.out.println("      public void close() { }");
            System.out.println("      @Override");
            System.out.println("      public Publisher<MessageMetadata> newPublisher(Partition partition) {");
            System.out.println("          return SinglePartitionPublisherBuilder.newBuilder()...");
            System.out.println("      }");
            System.out.println("  }");
        }
    }
}