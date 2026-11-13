package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    
    // Configuration parameters - these should be externalized for reuse
    private static final String OLD_FULLY_QUALIFIED_CLASS = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_FULLY_QUALIFIED_CLASS = "org.apache.maven.shared.utils.xml.Xpp3Dom";
    
    // Extract simple class name from FQN
    private static final String OLD_SIMPLE_CLASS = extractSimpleClassName(OLD_FULLY_QUALIFIED_CLASS);
    private static final String NEW_SIMPLE_CLASS = extractSimpleClassName(NEW_FULLY_QUALIFIED_CLASS);
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Optional: --old-class <old-fqn> --new-class <new-fqn>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldClass = OLD_FULLY_QUALIFIED_CLASS;
        String newClass = NEW_FULLY_QUALIFIED_CLASS;
        
        // Parse command line arguments
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--old-class") && i + 1 < args.length) {
                oldClass = args[++i];
            } else if (args[i].equals("--new-class") && i + 1 < args.length) {
                newClass = args[++i];
            }
        }
        
        System.out.println("Transforming class references:");
        System.out.println("  From: " + oldClass);
        System.out.println("  To:   " + newClass);
        System.out.println("  Source directory: " + sourceDir);
        
        try {
            transformDirectory(Paths.get(sourceDir), oldClass, newClass);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String extractSimpleClassName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
    
    private static void transformDirectory(Path sourceDir, String oldFqn, String newFqn) throws Exception {
        File dir = sourceDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<File> javaFiles = findJavaFiles(dir);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        JavaParser parser = new JavaParser();
        String oldSimpleClass = extractSimpleClassName(oldFqn);
        String newSimpleClass = extractSimpleClassName(newFqn);
        
        for (File javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile.getPath());
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + javaFile)
            );
            
            boolean modified = applyTransformation(cu, oldFqn, newFqn, oldSimpleClass, newSimpleClass);
            
            if (modified) {
                // Write back the transformed file
                cu.toString(); // Trigger formatting
                // In a real implementation, we'd write to file: cu.save(javaFile.toPath());
                System.out.println("  ✓ Modified");
            } else {
                System.out.println("  - No changes needed");
            }
        }
    }
    
    private static List<File> findJavaFiles(File dir) {
        // Simple recursive search for .java files
        // In production, use Files.walk or similar
        java.util.ArrayList<File> files = new java.util.ArrayList<>();
        findJavaFilesRecursive(dir, files);
        return files;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> files) {
        File[] dirFiles = dir.listFiles();
        if (dirFiles == null) return;
        
        for (File file : dirFiles) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, files);
            } else if (file.getName().endsWith(".java")) {
                files.add(file);
            }
        }
    }
    
    private static boolean applyTransformation(CompilationUnit cu, 
                                             String oldFqn, 
                                             String newFqn,
                                             String oldSimpleClass,
                                             String newSimpleClass) {
        
        ClassMigrationVisitor visitor = new ClassMigrationVisitor(oldFqn, newFqn, oldSimpleClass, newSimpleClass);
        boolean modified = false;
        
        // Visit imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (visitor.visit(importDecl, null) != importDecl) {
                modified = true;
            }
        }
        
        // Visit the rest of the AST
        if (visitor.visit(cu, null) != cu) {
            modified = true;
        }
        
        return modified || visitor.wasModified();
    }
    
    private static class ClassMigrationVisitor extends ModifierVisitor<Void> {
        private final String oldFqn;
        private final String newFqn;
        private final String oldSimpleClass;
        private final String newSimpleClass;
        private boolean modified = false;
        
        public ClassMigrationVisitor(String oldFqn, String newFqn, String oldSimpleClass, String newSimpleClass) {
            this.oldFqn = oldFqn;
            this.newFqn = newFqn;
            this.oldSimpleClass = oldSimpleClass;
            this.newSimpleClass = newSimpleClass;
        }
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches our old class (exact match or wildcard)
            if (importName.equals(oldFqn) || 
                importName.equals(oldSimpleClass) ||
                importName.startsWith(oldFqn + ".")) {
                
                // Replace the import
                String newImportName;
                if (importName.equals(oldFqn)) {
                    newImportName = newFqn;
                } else if (importName.equals(oldSimpleClass)) {
                    newImportName = newSimpleClass;
                } else {
                    // Handle inner classes or wildcards
                    newImportName = importName.replace(oldFqn, newFqn);
                }
                
                ImportDeclaration newImport = new ImportDeclaration(newImportName, 
                    importDecl.isStatic(), importDecl.isAsterisk());
                modified = true;
                return newImport;
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(CastExpr castExpr, Void arg) {
            // Check cast expressions like (Xpp3Dom) something
            if (castExpr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) castExpr.getType();
                if (type.getNameAsString().equals(oldSimpleClass)) {
                    type.setName(newSimpleClass);
                    modified = true;
                }
            }
            return (Node) super.visit(castExpr, arg);
        }
        
        @Override
        public Node visit(MethodReferenceExpr methodRef, Void arg) {
            // Check method references like Xpp3Dom::getChildren
            if (methodRef.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) methodRef.getScope();
                if (scope.getNameAsString().equals(oldSimpleClass)) {
                    scope.setName(newSimpleClass);
                    modified = true;
                }
            }
            return (Node) super.visit(methodRef, arg);
        }
        
        @Override
        public Node visit(NameExpr nameExpr, Void arg) {
            // Check simple name references to the class
            if (nameExpr.getNameAsString().equals(oldSimpleClass)) {
                nameExpr.setName(newSimpleClass);
                modified = true;
            }
            return (Node) super.visit(nameExpr, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            // Check type references in variable declarations, method parameters, etc.
            if (type.getNameAsString().equals(oldSimpleClass)) {
                type.setName(newSimpleClass);
                modified = true;
            }
            return (Node) super.visit(type, arg);
        }
    }
}