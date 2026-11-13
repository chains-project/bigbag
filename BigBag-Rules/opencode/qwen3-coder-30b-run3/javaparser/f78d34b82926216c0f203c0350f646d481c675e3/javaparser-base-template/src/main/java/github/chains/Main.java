package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <project-root-path>");
            System.exit(1);
        }
        
        String projectPath = args[0];
        try {
            processProject(projectPath);
            System.out.println("Dependency migration completed for: " + projectPath);
        } catch (Exception e) {
            System.err.println("Error during dependency migration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void processProject(String projectPath) throws IOException {
        Path rootPath = Paths.get(projectPath);
        Files.walk(rootPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Apply migration visitor to handle import rewrites
            MigrationVisitor visitor = new MigrationVisitor();
            cu.accept(visitor, null);
            
            // Save modified file
            String result = LexicalPreservingPrinter.print(cu);
            try (FileWriter writer = new FileWriter(javaFilePath.toFile())) {
                writer.write(result);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that performs the actual dependency migration
     */
    private static class MigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public com.github.javaparser.ast.Node visit(ImportDeclaration importDecl, Void arg) {
            Name name = importDecl.getName();
            
            // Handle regular imports from develop.p2p.lib to tokyo.peya.lib
            if (name.getIdentifier().startsWith("develop.p2p.lib")) {
                String newName = name.getIdentifier().replace("develop.p2p.lib", "tokyo.peya.lib");
                importDecl.setName(newName);
            }
            
            // Handle static imports from develop.p2p.lib to tokyo.peya.lib
            if (importDecl.isStatic() && name.getIdentifier().startsWith("develop.p2p.lib")) {
                String newName = name.getIdentifier().replace("develop.p2p.lib", "tokyo.peya.lib");
                importDecl.setName(newName);
            }
            
            // Handle wildcard imports
            if (name.getIdentifier().startsWith("develop.p2p.lib.*")) {
                String newName = name.getIdentifier().replace("develop.p2p.lib.*", "tokyo.peya.lib.*");
                importDecl.setName(newName);
            }
            
            return super.visit(importDecl, arg);
        }
    }
}