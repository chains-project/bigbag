package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    public static class ClassMigration {
        private final String oldFullyQualifiedName;
        private final String newFullyQualifiedName;
        
        public ClassMigration(String oldFullyQualifiedName, String newFullyQualifiedName) {
            this.oldFullyQualifiedName = oldFullyQualifiedName;
            this.newFullyQualifiedName = newFullyQualifiedName;
        }
        
        public String getOldFullyQualifiedName() { return oldFullyQualifiedName; }
        public String getNewFullyQualifiedName() { return newFullyQualifiedName; }
    }
    
    public static class ImportMigrationVisitor extends ModifierVisitor<Void> {
        private final List<ClassMigration> migrations;
        
        public ImportMigrationVisitor(List<ClassMigration> migrations) {
            this.migrations = migrations;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importedName = importDecl.getNameAsString();
            
            for (ClassMigration migration : migrations) {
                if (importedName.equals(migration.getOldFullyQualifiedName())) {
                    importDecl.setName(new Name(migration.getNewFullyQualifiedName()));
                    break;
                }
            }
            
            return super.visit(importDecl, arg);
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <oldFQN1:newFQN1> [<oldFQN2:newFQN2> ...]");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        List<ClassMigration> migrations = new ArrayList<>();
        
        for (int i = 1; i < args.length; i++) {
            String[] parts = args[i].split(":");
            if (parts.length != 2) {
                System.err.println("Invalid migration format: " + args[i] + ". Expected: oldFully.Qualified.Name:newFully.Qualified.Name");
                System.exit(1);
            }
            migrations.add(new ClassMigration(parts[0], parts[1]));
        }
        
        System.out.println("Applying " + migrations.size() + " class migrations:");
        for (ClassMigration migration : migrations) {
            System.out.println("  " + migration.getOldFullyQualifiedName() + " -> " + migration.getNewFullyQualifiedName());
        }
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                System.err.println("Source directory does not exist: " + sourceDir);
                System.exit(1);
            }
            
            List<Path> javaFiles = Files.walk(sourcePath)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            JavaParser javaParser = new JavaParser();
            ImportMigrationVisitor visitor = new ImportMigrationVisitor(migrations);
            
            int processed = 0;
            int modified = 0;
            
            for (Path javaFile : javaFiles) {
                try (FileInputStream fis = new FileInputStream(javaFile.toFile())) {
                    CompilationUnit cu = javaParser.parse(fis).getResult().orElse(null);
                    if (cu != null) {
                        CompilationUnit original = cu.clone();
                        cu.accept(visitor, null);
                        
                        if (!cu.equals(original)) {
                            String newContent = cu.toString();
                            Files.write(javaFile, newContent.getBytes());
                            modified++;
                            System.out.println("Modified: " + javaFile);
                        }
                        processed++;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("Processed " + processed + " files, modified " + modified + " files");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}