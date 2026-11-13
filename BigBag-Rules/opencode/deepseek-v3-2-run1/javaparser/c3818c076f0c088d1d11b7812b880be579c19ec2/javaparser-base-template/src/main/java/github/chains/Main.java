package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static class JakartaValidationMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String importName = n.getNameAsString();
            
            if (importName.startsWith("javax.validation")) {
                String newImportName = importName.replace("javax.validation", "jakarta.validation");
                n.setName(new Name(newImportName));
                System.out.println("Updated import: " + importName + " -> " + newImportName);
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String typeName = n.getNameAsString();
            
            if (typeName.startsWith("javax.validation.")) {
                String newTypeName = typeName.replace("javax.validation.", "jakarta.validation.");
                n.setName(newTypeName);
                System.out.println("Updated type reference: " + typeName + " -> " + newTypeName);
            }
            
            if (n.getScope().isPresent()) {
                Node scope = n.getScope().get();
                if (scope instanceof ClassOrInterfaceType) {
                    visit((ClassOrInterfaceType) scope, arg);
                } else if (scope instanceof Name) {
                    String scopeName = ((Name) scope).asString();
                    if (scopeName.startsWith("javax.validation")) {
                        String newScopeName = scopeName.replace("javax.validation", "jakarta.validation");
                        ((Name) scope).setIdentifier(newScopeName);
                        System.out.println("Updated scoped type: " + scopeName + "." + typeName + " -> " + newScopeName + "." + typeName);
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(Name n, Void arg) {
            String nameStr = n.asString();
            
            if (nameStr.startsWith("javax.validation")) {
                String newName = nameStr.replace("javax.validation", "jakarta.validation");
                n.setIdentifier(newName);
                System.out.println("Updated name: " + nameStr + " -> " + newName);
            }
            
            return (Node) super.visit(n, arg);
        }
    }
    
    public static void processFile(Path filePath) throws IOException {
        System.out.println("Processing: " + filePath);
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + filePath)
        );
        
        JakartaValidationMigrationVisitor visitor = new JakartaValidationMigrationVisitor();
        cu.accept(visitor, null);
        
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(cu.toString());
        }
    }
    
    public static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Migrating javax.validation to jakarta.validation in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            for (Path file : javaFiles) {
                try {
                    processFile(file);
                } catch (Exception e) {
                    System.err.println("Error processing " + file + ": " + e.getMessage());
                }
            }
            
            System.out.println("Migration completed successfully!");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}