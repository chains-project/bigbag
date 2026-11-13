package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            processJavaFiles(sourcePath);
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFiles(Path sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser(new ParserConfiguration());
            Optional<CompilationUnit> parseResult = parser.parse(filePath).getResult();
            
            if (parseResult.isPresent()) {
                CompilationUnit cu = parseResult.get();
                ToStringStrategy2FixVisitor visitor = new ToStringStrategy2FixVisitor();
                cu.accept(visitor, null);
                
                if (visitor.isModified()) {
                    Files.write(filePath, cu.toString().getBytes());
                    System.out.println("Modified: " + filePath);
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ToStringStrategy2FixVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is an appendField call on a ToStringStrategy2 instance
            if (isAppendFieldCall(n) && hasFiveParameters(n)) {
                // Add false as the last parameter
                n.getArguments().add(new BooleanLiteralExpr(false));
                modified = true;
                return n;
            }
            
            // Check if this is a getInstance() call on JAXBToStringStrategy
            if (isGetInstanceCall(n)) {
                // Replace method call with field access
                FieldAccessExpr fieldAccess = new FieldAccessExpr(n.getScope().get(), "INSTANCE");
                modified = true;
                return fieldAccess;
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isAppendFieldCall(MethodCallExpr n) {
            return "appendField".equals(n.getNameAsString());
        }
        
        private boolean hasFiveParameters(MethodCallExpr n) {
            return n.getArguments().size() == 5;
        }
        
        private boolean isGetInstanceCall(MethodCallExpr n) {
            return "getInstance".equals(n.getNameAsString()) && 
                   n.getScope().isPresent() &&
                   n.getScope().get().toString().contains("JAXBToStringStrategy");
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}