package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking API changes in Maven dependencies.
 * This rule demonstrates how to create reusable transformations for API breaking changes.
 * 
 * PATTERN: Method removal/rename in builder pattern
 * EXAMPLE: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>) was removed
 * 
 * USAGE:
 * 1. Investigate the new API to understand what replaced the old method
 * 2. Update configuration constants below
 * 3. Customize transformation logic if method signature changed
 * 4. Run on target source directory
 */
public class Main {
    
    // ===== CONFIGURATION =====
    // Update these based on your specific breaking change investigation
    
    // The old method name that no longer exists
    private static final String OLD_METHOD_NAME = "addEnabledLanguages";
    
    // The new method name (if renamed) or transformation strategy
    // Options:
    // - "setEnabledLanguages" if method was renamed
    // - "" if method was removed with no replacement (will comment out call)
    // - Custom transformation if signature changed
    private static final String NEW_METHOD_NAME = "setEnabledLanguages";
    
    // Optional: Target type pattern for more precise matching
    private static final String TARGET_TYPE_PATTERN = "AnalysisEngineConfiguration.Builder";
    
    // Transformation mode:
    // - "rename": Simple method rename (same signature)
    // - "comment_out": Comment out the method call if no replacement exists
    // - "custom": Implement custom transformation in transformMethodCall()
    private static final String TRANSFORMATION_MODE = "rename";
    
    // ===== MAIN EXECUTION =====
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("\nCurrent configuration for sonarlint-core 9.1.0.74321 breaking change:");
            System.err.println("  OLD_METHOD_NAME: " + OLD_METHOD_NAME);
            System.err.println("  NEW_METHOD_NAME: " + NEW_METHOD_NAME + " (CHANGE THIS based on actual new API)");
            System.err.println("  TARGET_TYPE_PATTERN: " + TARGET_TYPE_PATTERN);
            System.err.println("  TRANSFORMATION_MODE: " + TRANSFORMATION_MODE);
            System.err.println("\nInstructions:");
            System.err.println("  1. Check sonarlint-core 9.1.0.74321 javadoc for correct method");
            System.err.println("  2. Update NEW_METHOD_NAME constant above");
            System.err.println("  3. Recompile and run transformation");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        System.out.println("Configuration:");
        System.out.println("  Looking for: " + TARGET_TYPE_PATTERN + "." + OLD_METHOD_NAME + "()");
        System.out.println("  Replacing with: " + TARGET_TYPE_PATTERN + "." + NEW_METHOD_NAME + "()");
        System.out.println("  Mode: " + TRANSFORMATION_MODE);
        
        try {
            List<File> javaFiles = findAllJavaFiles(new File(sourceDir));
            int transformedFiles = 0;
            int transformedCalls = 0;
            
            for (File javaFile : javaFiles) {
                TransformationResult result = transformFile(javaFile);
                if (result.fileModified) {
                    transformedFiles++;
                    transformedCalls += result.callsTransformed;
                }
            }
            
            System.out.println("\n=== TRANSFORMATION SUMMARY ===");
            System.out.println("Modified files: " + transformedFiles);
            System.out.println("Transformed calls: " + transformedCalls);
            System.out.println("\nNEXT STEPS:");
            System.out.println("1. Check if " + NEW_METHOD_NAME + " is the correct replacement method");
            System.out.println("2. If not, investigate API and update NEW_METHOD_NAME constant");
            System.out.println("3. Recompile project to verify fix");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    // ===== FILE OPERATIONS =====
    
    private static List<File> findAllJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findAllJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }
    
    private static TransformationResult transformFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        MethodCallTransformer transformer = new MethodCallTransformer();
        transformer.visit(cu, null);
        
        if (transformer.wasModified()) {
            // Save the modified file
            try {
                javaFile.getParentFile().mkdirs();
                java.io.FileWriter writer = new java.io.FileWriter(javaFile);
                writer.write(cu.toString());
                writer.close();
                System.out.println("Modified: " + javaFile.getPath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to write file: " + javaFile, e);
            }
            return new TransformationResult(true, transformer.getCallsTransformed());
        }
        return new TransformationResult(false, 0);
    }
    
    // ===== TRANSFORMATION LOGIC =====
    
    private static class TransformationResult {
        final boolean fileModified;
        final int callsTransformed;
        
        TransformationResult(boolean fileModified, int callsTransformed) {
            this.fileModified = fileModified;
            this.callsTransformed = callsTransformed;
        }
    }
    
    private static class MethodCallTransformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private int callsTransformed = 0;
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            if (isTargetMethodCall(n)) {
                System.out.println("  Found " + OLD_METHOD_NAME + " at line: " + 
                    n.getRange().map(r -> r.begin.line).orElse(0));
                
                boolean transformed = transformMethodCall(n);
                if (transformed) {
                    modified = true;
                    callsTransformed++;
                }
            }
        }
        
        private boolean isTargetMethodCall(MethodCallExpr n) {
            return n.getNameAsString().equals(OLD_METHOD_NAME);
        }
        
        private boolean transformMethodCall(MethodCallExpr n) {
            switch (TRANSFORMATION_MODE) {
                case "rename":
                    // Simple rename - assumes same method signature
                    if (NEW_METHOD_NAME.isEmpty()) {
                        System.err.println("  ERROR: NEW_METHOD_NAME is empty for rename mode");
                        return false;
                    }
                    n.setName(NEW_METHOD_NAME);
                    System.out.println("  Renamed to: " + NEW_METHOD_NAME);
                    return true;
                    
                case "comment_out":
                    // Comment out method call if no replacement exists
                    // Note: This would require more complex AST manipulation
                    // For now, we'll rename to a placeholder and add a comment
                    String oldName = n.getNameAsString();
                    n.setName(oldName + "_DEPRECATED_REMOVED");
                    System.out.println("  Marked as deprecated/removed - manual fix required");
                    return true;
                    
                case "custom":
                    // Implement custom transformation here
                    // Example: Change from Set<Language> to List<Language>
                    // if (n.getArguments().size() == 1) {
                    //     // Transform argument if needed
                    //     n.setArguments(n.getArguments()); // Modify as needed
                    //     n.setName(NEW_METHOD_NAME);
                    //     return true;
                    // }
                    System.err.println("  Custom transformation not implemented");
                    return false;
                    
                default:
                    System.err.println("  Unknown transformation mode: " + TRANSFORMATION_MODE);
                    return false;
            }
        }
        
        public boolean wasModified() {
            return modified;
        }
        
        public int getCallsTransformed() {
            return callsTransformed;
        }
    }
}