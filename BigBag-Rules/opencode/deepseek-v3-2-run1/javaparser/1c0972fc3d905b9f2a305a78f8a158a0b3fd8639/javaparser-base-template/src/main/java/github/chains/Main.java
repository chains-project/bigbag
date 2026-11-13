package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    
    // Configuration: old class to replace and new class to use
    private static final String OLD_CLASS_FQN = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_CLASS_FQN = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String OLD_CLASS_SIMPLE_NAME = "Maven31DependencyGraphBuilder";
    private static final String NEW_CLASS_SIMPLE_NAME = "DefaultDependencyGraphBuilder";
    
    // Configuration for constructor adaptation
    // In this specific case, the old constructor had no args, new constructor requires ProjectDependenciesResolver
    // For a generic solution, we might need to provide constructor adaptation logic
    // For now, we'll just update the type and let compilation fail if constructor signature doesn't match
    // A more advanced solution would allow configuring constructor argument adaptation
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Java files in: " + sourceDir);
        System.out.println("Replacing " + OLD_CLASS_FQN + " with " + NEW_CLASS_FQN);
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
        
        System.out.println("Transformation complete.");
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            boolean modified = false;
            
            // Transform imports
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importName = importDecl.getNameAsString();
                if (importName.equals(OLD_CLASS_FQN)) {
                    importDecl.setName(NEW_CLASS_FQN);
                    modified = true;
                    System.out.println("  Updated import in " + filePath);
                }
            }
            
            // Transform type references and constructor calls using a visitor
            ClassReferenceVisitor visitor = new ClassReferenceVisitor();
            cu.accept(visitor, null);
            if (visitor.modified) {
                modified = true;
            }
            
            if (modified) {
                // Write back the transformed file
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                String transformedCode = printer.print(cu);
                Files.writeString(filePath, transformedCode);
                System.out.println("  Transformed: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ClassReferenceVisitor extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            // Check if this type reference is the old class
            if (type.getNameAsString().equals(OLD_CLASS_SIMPLE_NAME)) {
                // Check if it's qualified (e.g., org.example.OldClass)
                // or if we need to check context to avoid false positives
                type.setName(NEW_CLASS_SIMPLE_NAME);
                modified = true;
            }
            return super.visit(type, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Check if this is a constructor call for the old class
            if (expr.getType().getNameAsString().equals(OLD_CLASS_SIMPLE_NAME)) {
                // Replace with new class
                expr.getType().setName(NEW_CLASS_SIMPLE_NAME);
                modified = true;
                
                // Note: Constructor arguments may need adaptation
                // For this specific case: old constructor had no args, new one requires ProjectDependenciesResolver
                // This is a breaking change that requires manual intervention
                // We could add logic to adapt constructor calls based on configuration
                System.out.println("  WARNING: Constructor call for " + OLD_CLASS_SIMPLE_NAME + 
                                 " replaced with " + NEW_CLASS_SIMPLE_NAME + 
                                 ". Constructor signature may have changed and may require manual adaptation.");
            }
            return super.visit(expr, arg);
        }
    }
}