package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static class PackageMigrationVisitor extends ModifierVisitor<Void> {
        private final String oldPackagePrefix;
        private final String newPackagePrefix;
        private int changesCount = 0;
        
        public PackageMigrationVisitor(String oldPackagePrefix, String newPackagePrefix) {
            this.oldPackagePrefix = oldPackagePrefix;
            this.newPackagePrefix = newPackagePrefix;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(oldPackagePrefix)) {
                String newImportName = newPackagePrefix + importName.substring(oldPackagePrefix.length());
                importDecl.setName(newImportName);
                changesCount++;
                System.out.println("  Changed import: " + importName + " -> " + newImportName);
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            String nameStr = name.asString();
            if (nameStr.startsWith(oldPackagePrefix)) {
                String newName = newPackagePrefix + nameStr.substring(oldPackagePrefix.length());
                name.setIdentifier(newName);
                changesCount++;
            }
            return (Node) super.visit(name, arg);
        }
        
        public int getChangesCount() {
            return changesCount;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Main <sourceDir> <oldPackagePrefix> <newPackagePrefix>");
            System.err.println("Example: java Main /path/to/src javax.interceptor jakarta.interceptor");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackagePrefix = args[1];
        String newPackagePrefix = args[2];
        
        System.out.println("Migrating package references:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Old package prefix: " + oldPackagePrefix);
        System.out.println("  New package prefix: " + newPackagePrefix);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            JavaParser parser = new JavaParser();
            
            int totalFilesChanged = 0;
            int totalChanges = 0;
            
            for (Path filePath : javaFiles) {
                try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                    CompilationUnit cu = parser.parse(fis).getResult().orElse(null);
                    if (cu == null) {
                        System.err.println("Failed to parse: " + filePath);
                        continue;
                    }
                    
                    PackageMigrationVisitor visitor = new PackageMigrationVisitor(oldPackagePrefix, newPackagePrefix);
                    cu.accept(visitor, null);
                    
                    if (visitor.getChangesCount() > 0) {
                        System.out.println("Processing " + filePath + " (" + visitor.getChangesCount() + " changes)");
                        
                        String newContent = cu.toString();
                        try (FileWriter writer = new FileWriter(filePath.toFile())) {
                            writer.write(newContent);
                        }
                        
                        totalFilesChanged++;
                        totalChanges += visitor.getChangesCount();
                    }
                } catch (IOException e) {
                    System.err.println("Error processing " + filePath + ": " + e.getMessage());
                }
            }
            
            System.out.println("\nMigration completed:");
            System.out.println("  Total files changed: " + totalFilesChanged);
            System.out.println("  Total changes made: " + totalChanges);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        try (Stream<Path> stream = Files.walk(startDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
}