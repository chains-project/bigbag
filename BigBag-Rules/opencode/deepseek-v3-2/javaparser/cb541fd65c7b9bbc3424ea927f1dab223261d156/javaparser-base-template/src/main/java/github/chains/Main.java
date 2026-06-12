package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(File dir) throws FileNotFoundException, java.io.IOException {
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + dir.getPath());
        }
        
        List<File> javaFiles = findJavaFiles(dir);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        JavaParser parser = new JavaParser();
        int filesModified = 0;
        
        for (File javaFile : javaFiles) {
            Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                HamcrestMigrationVisitor visitor = new HamcrestMigrationVisitor();
                CompilationUnit modifiedCu = (CompilationUnit) cu.accept(visitor, null);
                
                if (visitor.wasModified()) {
                    // Update imports based on what's needed
                    updateImports(modifiedCu, visitor.needsMatchersImport(), visitor.needsCoreMatchersImport());
                    
                    // Write modified file
                    try (java.io.FileWriter writer = new java.io.FileWriter(javaFile)) {
                        writer.write(modifiedCu.toString());
                    } catch (java.io.IOException e) {
                        System.err.println("Error writing file " + javaFile.getPath() + ": " + e.getMessage());
                        throw e;
                    }
                    filesModified++;
                    System.out.println("Modified: " + javaFile.getPath());
                }
            }
        }
        
        System.out.println("Total files modified: " + filesModified);
    }
    
    private static void updateImports(CompilationUnit cu, boolean needsMatchersImport, boolean needsCoreMatchersImport) {
        // Remove existing Matchers and CoreMatchers imports
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            if (importName.equals("org.hamcrest.Matchers") || importName.equals("org.hamcrest.CoreMatchers")) {
                importsToRemove.add(imp);
            }
        }
        importsToRemove.forEach(cu::remove);
        
        // Add back imports as needed
        if (needsMatchersImport) {
            cu.addImport("org.hamcrest.Matchers");
        }
        if (needsCoreMatchersImport) {
            cu.addImport("org.hamcrest.CoreMatchers");
        }
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(dir, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> javaFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    /**
     * Visitor that migrates Hamcrest Matchers usage for Hamcrest 2.2 compatibility.
     * In Hamcrest 2.2, hamcrest-core and hamcrest-library are deprecated empty jars.
     * All functionality is in the main hamcrest jar.
     * Some methods are available in CoreMatchers, others only in Matchers.
     */
    private static class HamcrestMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private boolean needsMatchersImport = false;
        private boolean needsCoreMatchersImport = false;
        
        public boolean wasModified() {
            return modified || needsMatchersImport || needsCoreMatchersImport;
        }
        
        public boolean needsMatchersImport() {
            return needsMatchersImport;
        }
        
        public boolean needsCoreMatchersImport() {
            return needsCoreMatchersImport;
        }
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // First visit children to handle nested method calls
            super.visit(methodCall, arg);
            
            // Check if this is a static method call on Matchers
            if (methodCall.getScope().isPresent()) {
                Node scope = methodCall.getScope().get();
                
                if (scope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope;
                    String scopeName = nameExpr.getNameAsString();
                    
                    // Check for Matchers.methodName() calls
                    if (scopeName.equals("Matchers")) {
                        String methodName = methodCall.getNameAsString();
                        
                        // Check if this method is available in CoreMatchers
                        if (isMethodInCoreMatchers(methodName)) {
                            // Change Matchers.methodName() to CoreMatchers.methodName()
                            methodCall.setScope(new NameExpr("CoreMatchers"));
                            modified = true;
                            needsCoreMatchersImport = true;
                        } else {
                            // Method not in CoreMatchers, keep using Matchers
                            needsMatchersImport = true;
                        }
                    }
                }
            }
            
            return methodCall;
        }
        
        private boolean isMethodInCoreMatchers(String methodName) {
            // Common matchers that are available in CoreMatchers in Hamcrest 2.2
            // Based on analysis of Hamcrest 2.2 API
            switch (methodName) {
                case "containsString":
                case "endsWith":
                case "startsWith":
                case "equalTo":
                case "not":
                case "notNullValue":
                case "nullValue":
                case "sameInstance":
                case "instanceOf":
                case "any":
                case "allOf":
                case "anyOf":
                case "hasItem":
                case "hasItems":
                case "hasSize":
                case "empty":
                case "emptyArray":
                case "hasEntry":
                case "hasKey":
                case "hasValue":
                case "closeTo":
                case "greaterThan":
                case "greaterThanOrEqualTo":
                case "lessThan":
                case "lessThanOrEqualTo":
                case "is":
                    return true;
                    
                // These methods are NOT in CoreMatchers, only in Matchers
                case "emptyIterableOf":
                case "hasProperty":
                case "hasToString":
                case "typeCompatibleWith":
                case "contains":
                case "containsInAnyOrder":
                case "containsInRelativeOrder":
                case "array":
                case "arrayContaining":
                case "arrayContainingInAnyOrder":
                case "arrayWithSize":
                case "describedAs":
                default:
                    return false;
            }
        }
    }
}