package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    /**
     * Generic transformation rule for maven-archiver 3.6.0 breaking changes.
     * Fixes two deprecated method calls:
     * 1. MavenArchiver.configureReproducible(String) -> MavenArchiver.configureReproducibleBuild(String)
     * 2. MavenArchiver.parseOutputTimestamp(String) -> MavenArchiver.parseBuildOutputTimestamp(String)
     * 
     * This transformation is generic and can be applied to any Java project
     * affected by these maven-archiver API changes.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("  <source-directory>: Root directory containing Java source files to transform");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(new File(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (File javaFile : javaFiles) {
                boolean fileChanged = transformFile(javaFile);
                if (fileChanged) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation complete:");
            System.out.println("  Files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  Total transformations applied: " + totalTransformations);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Recursively find all Java files in a directory.
     */
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (!directory.exists() || !directory.isDirectory()) {
            return javaFiles;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return javaFiles;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                javaFiles.addAll(findJavaFiles(file));
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
        
        return javaFiles;
    }
    
    /**
     * Apply transformations to a single Java file.
     * Returns true if any transformations were applied.
     */
    private static boolean transformFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse file: " + javaFile)
        );
        
        MavenArchiverTransformer transformer = new MavenArchiverTransformer();
        
        // Apply transformations
        CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.wasTransformed()) {
            System.out.println("Transforming file: " + javaFile.getPath());
            System.out.println("  Applied " + transformer.getTransformationCount() + " transformation(s)");
            
            // Write the transformed content back to file
            try {
                // Use try-with-resources for proper file writing
                java.nio.file.Files.write(
                    javaFile.toPath(),
                    transformedCu.toString().getBytes()
                );
                return true;
            } catch (Exception e) {
                System.err.println("Error writing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms maven-archiver deprecated method calls.
     */
    private static class MavenArchiverTransformer extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        
        public boolean wasTransformed() {
            return transformationCount > 0;
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for MavenArchiver.configureReproducible(String)
            if (n.getNameAsString().equals("configureReproducible")) {
                // Check if this is a call on MavenArchiver instance
                if (isMavenArchiverMethodCall(n)) {
                    System.out.println("  Found deprecated: configureReproducible");
                    n.setName("configureReproducibleBuild");
                    transformationCount++;
                    System.out.println("    -> Transformed to: configureReproducibleBuild");
                }
            }
            
            // Check for MavenArchiver.parseOutputTimestamp(String)
            else if (n.getNameAsString().equals("parseOutputTimestamp")) {
                // Check if this is a call on MavenArchiver instance
                if (isMavenArchiverMethodCall(n)) {
                    System.out.println("  Found deprecated: parseOutputTimestamp");
                    
                    // Change from instance method to static method call
                    // parseOutputTimestamp(String) -> MavenArchiver.parseBuildOutputTimestamp(String)
                    // Create static method call: MavenArchiver.parseBuildOutputTimestamp(arg)
                    NameExpr staticTarget = new NameExpr("MavenArchiver");
                    MethodCallExpr newCall = new MethodCallExpr(staticTarget, "parseBuildOutputTimestamp");
                    
                    // Copy arguments
                    n.getArguments().forEach(argExpr -> newCall.addArgument(argExpr.clone()));
                    
                    transformationCount++;
                    System.out.println("    -> Transformed to: MavenArchiver.parseBuildOutputTimestamp");
                    
                    return newCall;
                }
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Check if a method call is likely on a MavenArchiver instance.
         * This is a heuristic - we check if the scope contains "MavenArchiver"
         * or if it's a method call that matches the deprecated signatures.
         */
        private boolean isMavenArchiverMethodCall(MethodCallExpr n) {
            // If there's no scope, we can't determine - assume it might be
            if (!n.getScope().isPresent()) {
                return true;
            }
            
            // Get the scope as string and check for MavenArchiver reference
            String scopeString = n.getScope().get().toString();
            return scopeString.contains("MavenArchiver") || 
                   scopeString.contains("mavenArchiver") ||
                   scopeString.contains("archiver");
        }
    }
}
