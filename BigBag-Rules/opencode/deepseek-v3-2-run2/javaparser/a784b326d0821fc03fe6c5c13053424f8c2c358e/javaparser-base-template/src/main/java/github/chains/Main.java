package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (File javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<File> findJavaFiles(String rootDir) throws IOException {
        List<File> javaFiles = new ArrayList<>();
        Path startPath = Paths.get(rootDir);
        
        Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        
        return javaFiles;
    }
    
    private static boolean processFile(File javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        FileInputStream in = new FileInputStream(javaFile);
        
        try {
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            if (cu == null) {
                return false;
            }
            
            boolean modified = false;
            ImportVisitor importVisitor = new ImportVisitor();
            importVisitor.visit(cu, null);
            
            TypeVisitor typeVisitor = new TypeVisitor();
            typeVisitor.visit(cu, null);
            
            if (importVisitor.isModified() || typeVisitor.isModified()) {
                modified = true;
                try (FileWriter writer = new FileWriter(javaFile)) {
                    writer.write(cu.toString());
                }
                System.out.println("Modified: " + javaFile.getPath());
            }
            
            return modified;
            
        } finally {
            in.close();
        }
    }
    
    private static class ImportVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.validation")) {
                String newImportName = importName.replace("javax.validation", "jakarta.validation");
                importDecl.setName(new Name(newImportName));
                modified = true;
                System.out.println("  Replaced import: " + importName + " -> " + newImportName);
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    private static class TypeVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);
            
            String typeName = type.getNameAsString();
            
            if (type.getScope().isPresent()) {
                ClassOrInterfaceType scope = type.getScope().get();
                String scopeName = scope.getNameAsString();
                if (scopeName.startsWith("javax.validation.")) {
                    String newScopeName = scopeName.replace("javax.validation.", "jakarta.validation.");
                    ClassOrInterfaceType newScope = new ClassOrInterfaceType(null, newScopeName);
                    type.setScope(newScope);
                    modified = true;
                    System.out.println("  Replaced scoped type: " + scopeName + "." + typeName + " -> " + newScopeName + "." + typeName);
                }
            } else if (typeName.startsWith("javax.validation.")) {
                String newTypeName = typeName.replace("javax.validation.", "jakarta.validation.");
                type.setName(newTypeName);
                modified = true;
                System.out.println("  Replaced type reference: " + typeName + " -> " + newTypeName);
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}