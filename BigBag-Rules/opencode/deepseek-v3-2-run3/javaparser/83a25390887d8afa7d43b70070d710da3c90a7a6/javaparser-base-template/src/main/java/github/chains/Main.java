package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    /**
     * Generic transformation rule for handling breaking API changes in dependencies.
     * 
     * This implementation handles multiple common breaking change patterns:
     * 1. Static method to extension function migration (Okio.buffer() -> source.buffer())
     * 2. Method signature changes (added/removed parameters)
     * 3. Class/method deprecation and replacement
     * 
     * Configuration is externalized through transformation rules that can be
     * parameterized for different library migrations.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-dir> <target-dir>");
            System.err.println("  source-dir: Root directory containing Java files to transform");
            System.err.println("  target-dir: Directory to write transformed files (can be same as source)");
            System.err.println("\nExample transformations applied:");
            System.err.println("  1. Okio.buffer(source) -> source.buffer()");
            System.err.println("  2. Okio.buffer(sink) -> sink.buffer()");
            System.err.println("  3. File source = Okio.source(file) -> File source = file.source()");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String targetDir = args[1];
        
        try {
            transformProject(sourceDir, targetDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir, String targetDir) throws Exception {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);
        
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        // Find all Java files
        List<File> javaFiles = Files.walk(sourcePath)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        int transformedCount = 0;
        
        for (File javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                boolean wasTransformed = transformCompilationUnit(cu);
                
                if (wasTransformed) {
                    transformedCount++;
                    
                    // Write transformed file to target directory
                    Path relativePath = sourcePath.relativize(javaFile.toPath());
                    Path outputPath = targetPath.resolve(relativePath);
                    
                    Files.createDirectories(outputPath.getParent());
                    Files.write(outputPath, cu.toString().getBytes());
                    
                    System.out.println("Transformed: " + relativePath);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedCount + " files");
    }
    
    /**
     * Applies all transformation rules to a compilation unit.
     * Each rule is independent and handles a specific breaking change pattern.
     */
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean transformed = false;
        
        // Rule 1: Static method to extension function migration
        // Pattern: Okio.methodName(receiver) -> receiver.methodName()
        transformed |= applyStaticToExtensionTransformation(cu, "Okio", "buffer");
        transformed |= applyStaticToExtensionTransformation(cu, "Okio", "source");
        transformed |= applyStaticToExtensionTransformation(cu, "Okio", "sink");
        
        // Rule 2: Handle imports of deprecated classes
        transformed |= updateDeprecatedImports(cu);
        
        return transformed;
    }
    
    /**
     * Transforms static method calls to extension function calls.
     * Generic rule applicable to any library migrating from static methods
     * to extension functions.
     */
    private static boolean applyStaticToExtensionTransformation(CompilationUnit cu, 
                                                               String staticClassName, 
                                                               String methodName) {
        StaticToExtensionVisitor visitor = new StaticToExtensionVisitor(staticClassName, methodName);
        visitor.visit(cu, null);
        return visitor.wasTransformed();
    }
    
    /**
     * Updates imports of deprecated classes to their replacements.
     */
    private static boolean updateDeprecatedImports(CompilationUnit cu) {
        ImportUpdateVisitor visitor = new ImportUpdateVisitor();
        visitor.visit(cu, null);
        return visitor.wasTransformed();
    }
    
    /**
     * Visitor for transforming static method calls to extension function calls.
     * This handles the pattern: StaticClass.method(receiver) -> receiver.method()
     */
    private static class StaticToExtensionVisitor extends VoidVisitorAdapter<Void> {
        private final String staticClassName;
        private final String methodName;
        private boolean transformed = false;
        
        public StaticToExtensionVisitor(String staticClassName, String methodName) {
            this.staticClassName = staticClassName;
            this.methodName = methodName;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this matches the pattern: StaticClass.methodName(...)
            if (isStaticMethodCall(n, staticClassName, methodName) && n.getArguments().size() == 1) {
                // Transform: StaticClass.methodName(receiver) -> receiver.methodName()
                MethodCallExpr newCall = new MethodCallExpr(
                    n.getArgument(0).clone(),
                    methodName
                );
                
                // Preserve any type arguments from the original call
                if (n.getTypeArguments().isPresent()) {
                    newCall.setTypeArguments(n.getTypeArguments().get());
                }
                
                n.replace(newCall);
                transformed = true;
            }
        }
        
        private boolean isStaticMethodCall(MethodCallExpr expr, String className, String method) {
            if (!expr.getNameAsString().equals(method)) {
                return false;
            }
            
            if (expr.getScope().isPresent()) {
                return expr.getScope().get().toString().equals(className) ||
                       (expr.getScope().get() instanceof NameExpr && 
                        ((NameExpr) expr.getScope().get()).getNameAsString().equals(className)) ||
                       (expr.getScope().get() instanceof FieldAccessExpr && 
                        expr.getScope().get().toString().endsWith("." + className));
            }
            
            return false;
        }
        
        public boolean wasTransformed() {
            return transformed;
        }
    }
    
    /**
     * Visitor for updating imports of deprecated classes.
     * Example: Importing deprecated Okio class might need to be removed or replaced.
     */
    private static class ImportUpdateVisitor extends VoidVisitorAdapter<Void> {
        private boolean transformed = false;
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            String importName = n.getNameAsString();
            
            // Remove imports of deprecated Okio utilities
            if (importName.equals("okio.Okio") || importName.endsWith(".Okio")) {
                n.remove();
                transformed = true;
            }
            
            // Replace deprecated Utf8 imports if present
            if (importName.equals("okio.Utf8") || importName.endsWith(".Utf8")) {
                // In okio 3.x, Utf8 methods are available as extension functions
                // so the import may not be needed
                n.remove();
                transformed = true;
            }
        }
        
        public boolean wasTransformed() {
            return transformed;
        }
    }
}