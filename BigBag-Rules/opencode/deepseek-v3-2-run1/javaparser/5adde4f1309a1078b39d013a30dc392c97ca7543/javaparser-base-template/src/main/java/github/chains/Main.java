package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking API changes in sonarlint-core 9.1.0.74321.
 * 
 * Breaking Change: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>) was removed.
 * 
 * Transformation: Removes calls to addEnabledLanguages(Set<Language>) from AnalysisEngineConfiguration.Builder.
 * 
 * This is a generic transformation that can be applied to any project affected by this breaking change.
 * The transformation identifies method calls matching the pattern and removes them from the AST.
 */
public class Main {
    
    // Configuration: method signature to remove
    private static final String TARGET_METHOD_NAME = "addEnabledLanguages";
    // Target type pattern (can be made configurable)
    private static final String TARGET_TYPE_PATTERN = "AnalysisEngineConfiguration.Builder";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms Java files in the specified directory to fix " + TARGET_METHOD_NAME + " breaking change");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Fixing: " + TARGET_TYPE_PATTERN + "." + TARGET_METHOD_NAME + "(Set<Language>) removal");
        
        try {
            List<Path> javaFiles = findAllJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int removedCalls = 0;
            
            for (Path javaFile : javaFiles) {
                Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
                if (cuOpt.isPresent()) {
                    CompilationUnit cu = cuOpt.get();
                    AddEnabledLanguagesRemover visitor = new AddEnabledLanguagesRemover();
                    CompilationUnit transformedCu = (CompilationUnit) cu.accept(visitor, null);
                    
                    if (visitor.isModified()) {
                        writeJavaFile(javaFile, transformedCu);
                        transformedFiles++;
                        removedCalls += visitor.getRemovedCallCount();
                        System.out.println("  Removed " + visitor.getRemovedCallCount() + " calls in " + javaFile.getFileName());
                    }
                }
            }
            
            System.out.println("\nTransformation Summary:");
            System.out.println("  Files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  Total calls removed: " + removedCalls);
            System.out.println("\nNote: If the removed method call was part of a method chain,");
            System.out.println("      the code may need additional manual fixes for proper chaining.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        return Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static Optional<CompilationUnit> parseJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            return parser.parse(javaFile).getResult();
        } catch (Exception e) {
            System.err.println("Error parsing file: " + javaFile + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    private static void writeJavaFile(Path javaFile, CompilationUnit cu) throws Exception {
        PrinterConfiguration config = new DefaultPrinterConfiguration();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
        String transformedCode = printer.print(cu);
        Files.write(javaFile, transformedCode.getBytes());
    }
    
    /**
     * Visitor that removes calls to addEnabledLanguages(Set<Language>) from AnalysisEngineConfiguration.Builder.
     * 
     * The transformation works by:
     * 1. Identifying method calls with name "addEnabledLanguages"
     * 2. Checking if they have exactly one argument (presumably Set<Language>)
     * 3. Removing the method call from the AST
     * 
     * For method chains, we need to be careful to maintain the chain structure.
     */
    private static class AddEnabledLanguagesRemover extends ModifierVisitor<Void> {
        private boolean modified = false;
        private int removedCallCount = 0;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children to handle nested method calls
            MethodCallExpr visited = (MethodCallExpr) super.visit(n, arg);
            
            // Check if this is the method we want to remove
            if (visited.getNameAsString().equals(TARGET_METHOD_NAME)) {
                // Check if it has one argument (Set<Language>)
                if (visited.getArguments().size() == 1) {
                    // We found a call to addEnabledLanguages with one argument
                    System.out.println("    Found: " + visited);
                    
                    // Get the scope (what the method is called on)
                    Optional<Expression> scopeOpt = visited.getScope();
                    if (scopeOpt.isPresent()) {
                        modified = true;
                        removedCallCount++;
                        
                        // Return the scope instead of the method call
                        // This effectively removes addEnabledLanguages() from the chain
                        System.out.println("    Removed, returning scope: " + scopeOpt.get());
                        return scopeOpt.get();
                    }
                }
            }
            
            return visited;
        }
        
        public boolean isModified() {
            return modified;
        }
        
        public int getRemovedCallCount() {
            return removedCallCount;
        }
    }
}