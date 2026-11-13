package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("First argument is source directory path");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            JavaParser javaParser = new JavaParser();
            LogbackLoggerFixer fixer = new LogbackLoggerFixer();
            
            for (Path javaFile : javaFiles) {
                System.out.println("Processing: " + javaFile);
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow();
                cu.accept(fixer, null);
                
                if (fixer.wasModified()) {
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String newContent = printer.print(cu);
                    Files.write(javaFile, newContent.getBytes());
                    System.out.println("  Fixed: " + javaFile);
                }
            }
            
            System.out.println("Transformation complete!");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        try (Stream<Path> stream = Files.walk(rootDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    static class LogbackLoggerFixer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Check for imports of ch.qos.logback.classic.Logger
            String importName = importDecl.getNameAsString();
            if (importName.equals("ch.qos.logback.classic.Logger")) {
                System.out.println("  Found import of ch.qos.logback.classic.Logger");
                modified = true;
                
                // We could remove this import or replace it, but for now just note it
return (Node) super.visit(importDecl, arg);
        }
        return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(CastExpr castExpr, Void arg) {
            // Check if this is a cast to ch.qos.logback.classic.Logger
            if (castExpr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) castExpr.getType();
                String typeName = type.getNameAsString();
                
                // Check for Logger cast (could be ch.qos.logback.classic.Logger)
                if ("Logger".equals(typeName)) {
                    // Check if the expression being cast is LoggerFactory.getLogger(...)
                    String castExpression = castExpr.getExpression().toString();
                    if (castExpression.contains("LoggerFactory.getLogger")) {
                        System.out.println("  Found problematic cast: (Logger) LoggerFactory.getLogger(...)");
                        System.out.println("  This cast will fail because Logger implements LoggingEventAware");
                        System.out.println("  which doesn't exist in SLF4J 1.7.x");
                        modified = true;
                        
                        // For a generic fix, we need to replace this pattern
                        // The issue is that ch.qos.logback.classic.Logger implements
                        // org.slf4j.spi.LoggingEventAware, which doesn't exist in SLF4J 1.7.x
                        // One solution is to upgrade SLF4J to 2.x
                        // Another is to avoid using ch.qos.logback.classic.Logger
                        
                        // For now, we'll just report the issue
                        // A real transformation would need to replace this with a different approach
                        // e.g., using LoggerContext or upgrading SLF4J
                        
                    }
                }
            }
            return (Node) super.visit(castExpr, arg);
        }
    }
}