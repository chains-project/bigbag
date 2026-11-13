package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration: Fully qualified type names that need transformation
    private static final String PUBLISHER_TYPE = "com.google.cloud.pubsublite.internal.Publisher";
    private static final String PUBLISH_METADATA_TYPE = "com.google.cloud.pubsublite.PublishMetadata";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            int transformedNodes = 0;
            
            for (Path javaFile : javaFiles) {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                boolean fileModified = false;
                
                // Find all type references that match Publisher<PublishMetadata>
                List<ClassOrInterfaceType> publisherTypes = cu.findAll(ClassOrInterfaceType.class, 
                    type -> isPublisherWithPublishMetadata(type));
                
                for (ClassOrInterfaceType publisherType : publisherTypes) {
                    // Remove the generic type parameter
                    publisherType.setTypeArguments((com.github.javaparser.ast.NodeList<com.github.javaparser.ast.type.Type>) null);
                    transformedNodes++;
                    fileModified = true;
                }
                
                // Also remove import statements for PublishMetadata
                cu.getImports().removeIf(importDecl -> 
                    importDecl.getNameAsString().equals(PUBLISH_METADATA_TYPE));
                
                // Fix PartitionPublisherFactory lambda usage
                List<com.github.javaparser.ast.expr.LambdaExpr> lambdaExprs = cu.findAll(com.github.javaparser.ast.expr.LambdaExpr.class);
                for (com.github.javaparser.ast.expr.LambdaExpr lambda : lambdaExprs) {
                    // Check if this lambda is being passed to setPublisherFactory
                    if (lambda.getParentNode().isPresent()) {
                        com.github.javaparser.ast.Node parent = lambda.getParentNode().get();
                        if (parent instanceof com.github.javaparser.ast.expr.MethodCallExpr) {
                            com.github.javaparser.ast.expr.MethodCallExpr methodCall = (com.github.javaparser.ast.expr.MethodCallExpr) parent;
                            if (methodCall.getNameAsString().equals("setPublisherFactory")) {
                                // Replace lambda with anonymous class implementation
                                replaceLambdaWithAnonymousClass(methodCall, lambda);
                                transformedNodes++;
                                fileModified = true;
                            }
                        }
                    }
                }
                
                if (fileModified) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            }
            
            System.out.println("Transformation complete:");
            System.out.println("  Files modified: " + transformedFiles);
            System.out.println("  Nodes transformed: " + transformedNodes);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Checks if a type is Publisher<PublishMetadata>
     */
    private static boolean isPublisherWithPublishMetadata(ClassOrInterfaceType type) {
        if (!type.getNameAsString().equals("Publisher")) {
            return false;
        }
        
        // Check if it has type arguments
        if (!type.getTypeArguments().isPresent()) {
            return false;
        }
        
        List<Type> typeArgs = type.getTypeArguments().get();
        if (typeArgs.size() != 1) {
            return false;
        }
        
        // Check if the type argument is PublishMetadata
        Type typeArg = typeArgs.get(0);
        if (typeArg instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType classType = (ClassOrInterfaceType) typeArg;
            return classType.getNameAsString().equals("PublishMetadata");
        }
        
        return false;
    }
    
    /**
     * Replaces a lambda expression with an anonymous class implementation
     * for PartitionPublisherFactory
     */
    private static void replaceLambdaWithAnonymousClass(
            com.github.javaparser.ast.expr.MethodCallExpr methodCall, 
            com.github.javaparser.ast.expr.LambdaExpr lambda) {
        
        // Extract the lambda body
        String lambdaBody = lambda.getBody().toString();
        
        // Remove setContext call from the lambda body since it's no longer available
        lambdaBody = lambdaBody.replace(".setContext(PubsubContext.of(FRAMEWORK))", "");
        
        // Create anonymous class code
        String anonymousClassCode = "new com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory() {\n" +
            "    @Override\n" +
            "    public com.google.cloud.pubsublite.internal.Publisher newPublisher(com.google.cloud.pubsublite.Partition partition) throws com.google.api.gax.rpc.ApiException {\n" +
            "        return " + lambdaBody + ";\n" +
            "    }\n" +
            "    \n" +
            "    @Override\n" +
            "    public void close() {\n" +
            "        // No-op implementation\n" +
            "    }\n" +
            "}";
        
        // Parse the anonymous class expression
        com.github.javaparser.ast.expr.Expression anonymousClassExpr = 
            StaticJavaParser.parseExpression(anonymousClassCode);
        
        // Replace the lambda argument with the anonymous class
        methodCall.setArgument(0, anonymousClassExpr);
    }
}