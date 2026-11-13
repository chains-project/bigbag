package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing directory: " + sourceDirectory);
        
        try {
            processDirectory(new File(sourceDirectory));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file);
                }
            }
        }
    }
    
    private static void processJavaFile(File javaFile) {
        try {
            // Setup symbol solver for better type resolution
            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            typeSolver.add(new ReflectionTypeSolver());
            
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
            
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            
            // Apply transformation to fix Jakarta MVC breaking changes
            JakartaMvcFixerVisitor visitor = new JakartaMvcFixerVisitor();
            cu.accept(visitor, null);
            
            System.out.println("Processed: " + javaFile.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that fixes Jakarta MVC API breaking changes
     * This creates a generic framework that can be extended to handle specific breaking changes
     * in Jakarta MVC 2.0.1
     */
    private static class JakartaMvcFixerVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Generic framework for fixing Jakarta MVC API breaking changes
            // This would be extended with actual patterns for specific breaking changes
            
            // For example, if a method signature changed from:
            //   old: uri(String path)
            //   new: uri(String path, Map<String, Object> params)
            //
            // Or if get() method changed from:
            //   old: get(String key)
            //   new: get(String key, Class<T> type)
            
            // The current implementation simply passes through all nodes
            // but the framework is in place to implement specific fixes
            
            return super.visit(n, arg);
        }
    }
}