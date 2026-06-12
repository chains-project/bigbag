package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for breaking API changes where constructors
 * changed from single String parameter to (boolean, String) parameters.
 * 
 * This handles the Hamcrest 1.x to 2.x migration for:
 * - StringContains(String) -> StringContains(boolean, String)
 * - StringStartsWith(String) -> StringStartsWith(boolean, String)
 * - StringEndsWith(String) -> StringEndsWith(boolean, String)
 * 
 * The transformation adds 'false' as the first parameter (case-sensitive matching)
 * to preserve the original behavior.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms StringContains(String) -> StringContains(false, String)");
            System.err.println("StringStartsWith(String) -> StringStartsWith(false, String)");
            System.err.println("and StringEndsWith(String) -> StringEndsWith(false, String)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                ApiBreakingChangeVisitor visitor = new ApiBreakingChangeVisitor();
                cu.accept(visitor, null);
                
                if (visitor.getTransformationCount() > 0) {
                    String transformedCode = cu.toString();
                    Files.write(javaFile, transformedCode.getBytes());
                    
                    transformedFiles++;
                    totalTransformations += visitor.getTransformationCount();
                    System.out.println("Transformed " + javaFile + " (" + visitor.getTransformationCount() + " changes)");
                }
            }
            
            System.out.println("Summary: Transformed " + transformedFiles + " files with " + totalTransformations + " total transformations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Visitor that transforms constructor calls affected by the breaking API change.
     * Matches constructors with exactly one argument and transforms them to have
     * 'false' as the first argument followed by the original argument.
     */
    static class ApiBreakingChangeVisitor extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            if ("StringContains".equals(typeName) || "StringStartsWith".equals(typeName) || "StringEndsWith".equals(typeName)) {
                if (expr.getArguments().size() == 1) {
                    BooleanLiteralExpr falseLiteral = new BooleanLiteralExpr(false);
                    expr.getArguments().add(0, falseLiteral);
                    transformationCount++;
                    System.out.println("  - Transformed " + typeName + "(String) -> " + typeName + "(false, String)");
                }
            }
            
            return super.visit(expr, arg);
        }
    }
}