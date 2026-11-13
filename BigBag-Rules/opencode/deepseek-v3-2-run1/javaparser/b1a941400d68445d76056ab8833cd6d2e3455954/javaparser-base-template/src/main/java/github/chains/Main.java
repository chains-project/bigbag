package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int totalChanges = 0;
            for (Path javaFile : javaFiles) {
                int changes = processFile(javaFile);
                totalChanges += changes;
                if (changes > 0) {
                    System.out.println("Modified " + javaFile + " (" + changes + " changes)");
                }
            }
            
            System.out.println("Total changes made: " + totalChanges);
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processFile(Path filePath) {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        try {
            cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse " + filePath, e);
        }
        
        SnakeYamlConstructorFixVisitor visitor = new SnakeYamlConstructorFixVisitor();
        int changes = visitor.getChangesCount();
        cu.accept(visitor, null);
        
        int newChanges = visitor.getChangesCount() - changes;
        
        if (newChanges > 0) {
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String modifiedContent = printer.print(cu);
            try {
                Files.write(filePath, modifiedContent.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to write modified file: " + filePath, e);
            }
        }
        
        return newChanges;
    }
    
    static class SnakeYamlConstructorFixVisitor extends ModifierVisitor<Void> {
        private int changesCount = 0;
        
        public int getChangesCount() {
            return changesCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Constructor object creation
            if (n.getType().toString().equals("Constructor") || 
                n.getType().toString().equals("org.yaml.snakeyaml.constructor.Constructor")) {
                
                // Check if it has no arguments (the old API)
                if (n.getArguments().isEmpty()) {
                    System.out.println("Found Constructor() without arguments at line " + 
                        n.getRange().map(r -> r.begin.line).orElse(-1));
                    
                    // Create the replacement: new Constructor(new org.yaml.snakeyaml.LoaderOptions())
                    // Use fully qualified name to avoid import issues
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType("org.yaml.snakeyaml.LoaderOptions");
                    
                    // Replace the node
                    n.getArguments().add(loaderOptions);
                    changesCount++;
                    
                    System.out.println("  -> Replaced with Constructor(new org.yaml.snakeyaml.LoaderOptions())");
                }
            }
            
            return super.visit(n, arg);
        }
    }
}