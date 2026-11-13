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
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SimpleTransformer {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java github.chains.SimpleTransformer <source-directory> <old-fqn> <new-fqn>");
            System.err.println("Example: java github.chains.SimpleTransformer ./src org.codehaus.plexus.util.xml.Xpp3Dom org.apache.maven.shared.utils.xml.Xpp3Dom");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldFqn = args[1];
        String newFqn = args[2];
        
        System.out.println("Transforming class references:");
        System.out.println("  From: " + oldFqn);
        System.out.println("  To:   " + newFqn);
        System.out.println("  Source directory: " + sourceDir);
        
        transformDirectory(Paths.get(sourceDir), oldFqn, newFqn);
        System.out.println("Transformation completed successfully.");
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
                try (FileWriter writer = new FileWriter(javaFile)) {
                    writer.write(cu.toString());
                }
                System.out.println("  ✓ Modified and saved");
            } else {
                System.out.println("  - No changes needed");
            }
        }
    }
    
    private static String extractSimpleClassName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> files = new ArrayList<>();
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
            
            // Check if this import matches our old class
            if (importName.equals(oldFqn)) {
                ImportDeclaration newImport = new ImportDeclaration(newFqn, 
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