package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these should be parameterized for different dependency changes
    private static final String DEPENDENCY_BASE_PACKAGE = "com.google.api.services.cloudresourcemanager.v3";
    private static final String[] AFFECTED_CLASSES = {
        "CloudResourceManager.Projects",
        "CloudResourceManager.Folders", 
        "CloudResourceManager.Organizations"
    };
    private static final String[] AFFECTED_METHODS = {
        "getIamPolicy",
        "setIamPolicy", 
        "testIamPermissions"
    };
    private static final String REQUEST_CLASS_SUFFIX = "Request";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        JavaParser javaParser = new JavaParser(new ParserConfiguration());
        
        for (File javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile)) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) continue;
                
                IAMMethodTransformer transformer = new IAMMethodTransformer(javaParser);
                cu.accept(transformer, null);
                
                if (transformer.getTransformationCount() > 0) {
                    try (FileOutputStream out = new FileOutputStream(javaFile)) {
                        out.write(cu.toString().getBytes());
                    }
                    transformedFiles++;
                    transformedCalls += transformer.getTransformationCount();
                    System.out.println("Transformed " + transformer.getTransformationCount() + " calls in " + javaFile.getName());
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("- Files modified: " + transformedFiles);
        System.out.println("- Method calls transformed: " + transformedCalls);
    }
    
    static class IAMMethodTransformer extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        private JavaParser javaParser;
        
        public IAMMethodTransformer(JavaParser javaParser) {
            this.javaParser = javaParser;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a method call we need to transform
            if (isAffectedMethodCall(n)) {
                // Transform single parameter call to two parameter call
                if (n.getArguments().size() == 1) {
                    String resourceArg = n.getArguments().get(0).toString();
                    
                    // Determine the request class name based on method name
                    String methodName = n.getNameAsString();
                    String requestClassName = buildRequestClassName(methodName);
                    
                    // Create the new method call with two parameters
                    n.getArguments().clear();
                    n.getArguments().add(javaParser.parseExpression(resourceArg).getResult().orElseThrow());
                    n.getArguments().add(javaParser.parseExpression(
                        requestClassName + ".newBuilder().setResource(" + resourceArg + ").build()"
                    ).getResult().orElseThrow());
                    
                    transformationCount++;
                    System.out.println("Transformed: " + n.toString());
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isAffectedMethodCall(MethodCallExpr n) {
            String methodName = n.getNameAsString();
            
            // Check if method name matches affected methods
            boolean methodMatches = false;
            for (String affectedMethod : AFFECTED_METHODS) {
                if (affectedMethod.equals(methodName)) {
                    methodMatches = true;
                    break;
                }
            }
            
            if (!methodMatches) return false;
            
            // Check if scope matches affected classes
            if (n.getScope().isPresent()) {
                String scopeString = n.getScope().get().toString();
                for (String affectedClass : AFFECTED_CLASSES) {
                    if (scopeString.contains(affectedClass)) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        private String buildRequestClassName(String methodName) {
            // Convert method name to request class name
            // e.g., "getIamPolicy" -> "GetIamPolicyRequest"
            String className = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            return DEPENDENCY_BASE_PACKAGE + ".model." + className + REQUEST_CLASS_SUFFIX;
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
    }
}