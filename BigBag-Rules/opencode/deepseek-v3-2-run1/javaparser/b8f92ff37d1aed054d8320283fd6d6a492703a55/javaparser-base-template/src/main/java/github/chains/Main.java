package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        try {
            transformAllJavaFiles(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformAllJavaFiles(String sourceDir) throws IOException {
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        // Setup type solver for JavaParser
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                boolean fileModified = false;
                
                // Find and transform all TestListResolver.getWildcard() calls
                List<MethodCallExpr> wildcardCalls = cu.findAll(MethodCallExpr.class, 
                    methodCall -> isTestListResolverGetWildcard(methodCall));
                
                for (MethodCallExpr methodCall : wildcardCalls) {
                    // Replace method call with new TestListResolver("*")
                    ObjectCreationExpr newExpr = new ObjectCreationExpr();
                    newExpr.setType(new ClassOrInterfaceType(null, "TestListResolver"));
                    newExpr.addArgument("\"*\"");
                    
                    methodCall.replace(newExpr);
                    fileModified = true;
                    totalTransformations++;
                }
                
                if (fileModified) {
                    // Write the transformed file back
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(cu.toString());
                    }
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Summary: Transformed " + transformedFiles + " files with " + 
                          totalTransformations + " total replacements.");
    }
    
    private static boolean isTestListResolverGetWildcard(MethodCallExpr methodCall) {
        try {
            // Check if this is a method call on TestListResolver class
            if (!methodCall.getNameAsString().equals("getWildcard")) {
                return false;
            }
            
            // Check if it's a static method call (no scope means it could be static import)
            // or check the scope
            if (!methodCall.getScope().isPresent()) {
                // Could be a static import: getWildcard()
                // We need to check imports in the compilation unit
                return true; // We'll be conservative and transform it
            }
            
            // Check if scope is TestListResolver class
            String scopeString = methodCall.getScope().get().toString();
            return scopeString.equals("TestListResolver") || 
                   scopeString.endsWith(".TestListResolver");
            
        } catch (Exception e) {
            // If we can't determine, be conservative and transform it
            return methodCall.getNameAsString().equals("getWildcard");
        }
    }
}