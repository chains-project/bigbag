package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory> <old-fqcn> <new-fqcn>");
            System.err.println("Example: java -jar javaparser.jar /path/to/src org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldFqcn = args[1];
        String newFqcn = args.length > 2 ? args[2] : "";
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Migrating from: " + oldFqcn);
        System.out.println("Migrating to: " + newFqcn);
        
        Path startPath = new File(sourceDir).toPath();
        Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    processJavaFile(file, oldFqcn, newFqcn);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Transformation completed.");
    }
    
    private static void processJavaFile(Path filePath, String oldFqcn, String newFqcn) throws IOException {
        JavaParser parser = new JavaParser();
        FileInputStream in = new FileInputStream(filePath.toFile());
        
        try {
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            ClassMigrationVisitor visitor = new ClassMigrationVisitor(oldFqcn, newFqcn);
            cu.accept(visitor, null);
            
            if (visitor.wasModified()) {
                System.out.println("Modified: " + filePath);
                FileOutputStream out = new FileOutputStream(filePath.toFile());
                out.write(cu.toString().getBytes());
                out.close();
            }
        } finally {
            in.close();
        }
    }
    
    static class ClassMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private final String oldFqcn;
        private final String newFqcn;
        private final String oldPackage;
        private final String oldClassName;
        private final String newPackage;
        private final String newClassName;
        
        public ClassMigrationVisitor(String oldFqcn, String newFqcn) {
            this.oldFqcn = oldFqcn;
            this.newFqcn = newFqcn;
            
            int lastDotOld = oldFqcn.lastIndexOf('.');
            this.oldPackage = lastDotOld > 0 ? oldFqcn.substring(0, lastDotOld) : "";
            this.oldClassName = lastDotOld > 0 ? oldFqcn.substring(lastDotOld + 1) : oldFqcn;
            
            int lastDotNew = newFqcn.lastIndexOf('.');
            this.newPackage = lastDotNew > 0 ? newFqcn.substring(0, lastDotNew) : "";
            this.newClassName = lastDotNew > 0 ? newFqcn.substring(lastDotNew + 1) : newFqcn;
        }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            
            if (importName.equals(oldFqcn)) {
                id.setName(newFqcn);
                modified = true;
                System.out.println("  Updated import: " + oldFqcn + " -> " + newFqcn);
            }
            
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String fullName = getFullTypeName(type);
            if (fullName.equals(oldFqcn)) {
                updateTypeToNewPackage(type);
                modified = true;
                System.out.println("  Updated type reference: " + oldFqcn + " -> " + newFqcn);
            }
            
            return (Node) super.visit(type, arg);
        }
        
        private String getFullTypeName(ClassOrInterfaceType type) {
            if (type.getScope().isPresent()) {
                return getFullTypeName(type.getScope().get()) + "." + type.getNameAsString();
            }
            return type.getNameAsString();
        }
        
        private void updateTypeToNewPackage(ClassOrInterfaceType type) {
            int lastDot = newFqcn.lastIndexOf('.');
            if (lastDot > 0) {
                String newScope = newFqcn.substring(0, lastDot);
                String newSimpleName = newFqcn.substring(lastDot + 1);
                type.setScope(createScopeFromString(newScope));
                type.setName(newSimpleName);
            }
        }
        
        private ClassOrInterfaceType createScopeFromString(String packageName) {
            String[] parts = packageName.split("\\.");
            ClassOrInterfaceType scope = new ClassOrInterfaceType(null, parts[0]);
            for (int i = 1; i < parts.length; i++) {
                scope = new ClassOrInterfaceType(scope, parts[i]);
            }
            return scope;
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}