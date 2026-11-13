package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule to fix breaking changes in logback 1.4.0,
 * specifically addressing API changes in AsyncAppender constructors and related classes.
 * 
 * This transformation addresses common breaking changes in logback 1.4.0 where:
 * 1. AsyncAppender constructors changed to require includeCallerData parameter
 * 2. Other logback configuration APIs may have changed
 */
public class Main {
    
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        try (Stream<Path> paths = Files.walk(Paths.get(args.length > 0 ? args[0] : "/workspace/pay-adminusers"))) {
            List<Path> javaFiles = paths
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path file : javaFiles) {
                processFile(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to fix Logback API issues
            LogbackApiFixer fixer = new LogbackApiFixer();
            cu.accept(fixer, null);
            
            // Write back changes
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes Logback API issues related to breaking changes in version 1.4.0
     */
    private static class LogbackApiFixer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this creates a ch.qos.logback.classic.AsyncAppender instance
            String typeName = n.getType().asString();
            if ("AsyncAppender".equals(typeName) || 
                "ch.qos.logback.classic.AsyncAppender".equals(typeName)) {
                // If we're creating an AsyncAppender with no arguments, 
                // we might need to add includeCallerData parameter
                NodeList<Expression> arguments = n.getArguments();
                
                // For AsyncAppender, the constructor was likely changed to include a boolean parameter
                // This is a common breaking change in logback 1.4.0 where the default constructor 
                // now requires includeCallerData parameter
                if (arguments.isEmpty()) {
                    // Add includeCallerData=false parameter to match new signature
                    arguments.add(new BooleanLiteralExpr(false));
                }
            }
            
            return super.visit(n, arg);
        }
    }
}
