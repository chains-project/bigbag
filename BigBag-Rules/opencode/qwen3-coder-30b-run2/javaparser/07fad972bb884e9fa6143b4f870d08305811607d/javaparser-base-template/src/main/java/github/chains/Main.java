package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/pay-adminusers/src";
        
        System.out.println("Processing Java files in: " + sourceDir);
        
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to remove references to LoggingEventAware
            LoggingEventAwareRemover visitor = new LoggingEventAwareRemover();
            visitor.visit(cu, null);
            
            // Print the modified code back to the file
            String output = LexicalPreservingPrinter.print(cu);
            Files.write(filePath, output.getBytes());
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that removes references to org.slf4j.spi.LoggingEventAware
     */
    private static class LoggingEventAwareRemover extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Remove LoggingEventAware from implemented interfaces
            n.getImplementedTypes().removeIf(type -> {
                String typeName = type.asString();
                return typeName.contains("LoggingEventAware");
            });
            
            return super.visit(n, arg);
        }
    }
}
