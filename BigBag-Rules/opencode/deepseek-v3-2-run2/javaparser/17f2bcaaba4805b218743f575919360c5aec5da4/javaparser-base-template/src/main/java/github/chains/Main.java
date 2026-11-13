package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation rule to fix breaking API changes in tinspin-indexes 1.8.0
 * 
 * This transformation handles the change from:
 * - CoverTree.create(int dimensions, int maxPoints, PointDistanceFunction)
 * To:
 * - CoverTree.create(int dimensions, double base, PointDistanceFunction)
 * 
 * The transformation converts the second integer argument to a double value.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int processedFiles = 0;
            int transformedCalls = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                    CoverTreeTransformer transformer = new CoverTreeTransformer();
                    
                    int transformations = transformer.transform(cu);
                    if (transformations > 0) {
                        Files.write(javaFile, cu.toString().getBytes());
                        transformedCalls += transformations;
                        System.out.println("Transformed " + transformations + " calls in " + javaFile.getFileName());
                    }
                    
                    processedFiles++;
                } catch (IOException e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("Processed " + processedFiles + " files, transformed " + transformedCalls + " CoverTree.create calls");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that transforms CoverTree.create method calls with integer maxPoints
     * to use double base parameter instead.
     */
    private static class CoverTreeTransformer extends ModifierVisitor<Void> {
        
        private int transformationCount = 0;
        
        public int transform(CompilationUnit cu) {
            transformationCount = 0;
            cu.accept(this, null);
            return transformationCount;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a CoverTree.create call with 3 arguments
            if (isCoverTreeCreateCall(n) && n.getArguments().size() == 3) {
                // Check if the second argument is an integer literal
                if (n.getArgument(1).isIntegerLiteralExpr()) {
                    // Convert integer literal to double literal
                    String intValue = n.getArgument(1).asIntegerLiteralExpr().getValue();
                    DoubleLiteralExpr doubleExpr = new DoubleLiteralExpr(intValue + ".0");
                    n.getArgument(1).replace(doubleExpr);
                    transformationCount++;
                }
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Check if this method call matches the pattern:
         * CoverTree.create(...) or org.tinspin.index.covertree.CoverTree.create(...)
         */
        private boolean isCoverTreeCreateCall(MethodCallExpr n) {
            if (!n.getNameAsString().equals("create")) {
                return false;
            }
            
            // Check if the scope is CoverTree
            if (n.getScope().isPresent()) {
                Node scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String scopeName = ((NameExpr) scope).getNameAsString();
                    return scopeName.equals("CoverTree") || 
                           scopeName.equals("org.tinspin.index.covertree.CoverTree");
                }
            }
            
            return false;
        }
    }
}