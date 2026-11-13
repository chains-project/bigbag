package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - can be changed for different breaking changes
    private static final String TARGET_TYPE = "org.kohsuke.github.GHCompare";
    private static final String FIELD_NAME = "status";
    private static final String GETTER_METHOD = "getStatus";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Target type: " + TARGET_TYPE);
        System.out.println("Field to replace: " + FIELD_NAME);
        System.out.println("Replacement method: " + GETTER_METHOD);
        
        // Setup type solver for symbol resolution
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalTransformations = 0;
        int filesModified = 0;
        
        for (File javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile)) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                // Find all field access expressions
                List<FieldAccessExpr> fieldAccesses = cu.findAll(FieldAccessExpr.class);
                int transformationsInFile = 0;
                
                for (FieldAccessExpr fieldAccess : fieldAccesses) {
                    // Check if this is accessing our target field
                    if (FIELD_NAME.equals(fieldAccess.getNameAsString())) {
                        try {
                            // Try to resolve the type of the scope (the object before the dot)
                            String resolvedType = fieldAccess.getScope().calculateResolvedType().describe();
                            
                            // Check if the resolved type matches our target type
                            if (resolvedType.equals(TARGET_TYPE)) {
                                // Replace field access with method call
                                MethodCallExpr methodCall = new MethodCallExpr(
                                    fieldAccess.getScope().clone(), 
                                    GETTER_METHOD
                                );
                                fieldAccess.replace(methodCall);
                                transformationsInFile++;
                                System.out.println("  Transformed at: " + javaFile + " line " + 
                                    fieldAccess.getRange().map(r -> r.begin.line).orElse(-1));
                            }
                        } catch (Exception e) {
                            // Type resolution might fail for various reasons
                            // We'll use a fallback heuristic approach
                            if (shouldTransformHeuristic(fieldAccess)) {
                                MethodCallExpr methodCall = new MethodCallExpr(
                                    fieldAccess.getScope().clone(), 
                                    GETTER_METHOD
                                );
                                fieldAccess.replace(methodCall);
                                transformationsInFile++;
                                System.out.println("  Transformed (heuristic) at: " + javaFile + " line " + 
                                    fieldAccess.getRange().map(r -> r.begin.line).orElse(-1));
                            }
                        }
                    }
                }
                
                if (transformationsInFile > 0) {
                    // Write the modified file back
                    try (FileWriter writer = new FileWriter(javaFile)) {
                        writer.write(cu.toString());
                    }
                    filesModified++;
                    totalTransformations += transformationsInFile;
                    System.out.println("  Modified " + javaFile + " (" + transformationsInFile + " transformations)");
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    // Heuristic fallback when type resolution fails
    private static boolean shouldTransformHeuristic(FieldAccessExpr fieldAccess) {
        // Check if the scope expression looks like it could be a GHCompare
        String scopeCode = fieldAccess.getScope().toString();
        
        // Common patterns for GHCompare access:
        // - Ends with .getCompare(...)
        // - Variable name contains "compare"
        // - Method call to something that returns GHCompare
        
        // For safety, we can be more restrictive
        // Since we're transforming .status -> .getStatus(), we need to be careful
        // This is a simple heuristic - in production, you'd want more robust checks
        
        return scopeCode.contains("getCompare") || 
               scopeCode.contains("Compare") ||
               scopeCode.matches(".*[cC]ompare.*");
    }
}
