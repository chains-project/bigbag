package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple demonstration of JavaParser-based transformation to fix breaking API changes.
 * 
 * Demonstrates how to detect and potentially fix ScriptResult usage issues
 * that occur when upgrading acceptance-test-harness dependency.
 */
public class Main {
    
    public static void main(String[] args) {
        // Usage: specify the path to the project to transform
        if (args.length == 0) {
            System.out.println("Usage: java Main <project-path>");
            return;
        }
        
        String projectPath = args[0];
        
        // Process all Java files in the project
        try {
            processJavaFiles(projectPath);
            System.out.println("Demonstration completed successfully!");
        } catch (IOException e) {
            System.err.println("Failed to process files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process all Java files in the given directory recursively
     */
    public static void processJavaFiles(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir)) {
            throw new IOException("Directory does not exist: " + directoryPath);
        }
        
        // Process all Java files
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    /**
     * Process a single Java file
     */
    public static void processJavaFile(Path javaFilePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Apply transformation for breaking API changes
            TransformationVisitor visitor = new TransformationVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file using lexical preserving printer to maintain formatting
            String modifiedCode = LexicalPreservingPrinter.print(cu);
            try (FileOutputStream fos = new FileOutputStream(javaFilePath.toFile())) {
                fos.write(modifiedCode.getBytes());
            }
            
            System.out.println("Processed: " + javaFilePath);
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that demonstrates detection of breaking API patterns
     */
    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Detect ScriptResult instantiation pattern
            if (methodCall.getNameAsString().equals("new")) {
                if (methodCall.getScope().isPresent() && 
                    methodCall.getScope().get() instanceof NameExpr) {
                    NameExpr scope = (NameExpr) methodCall.getScope().get();
                    if (scope.getNameAsString().equals("ScriptResult")) {
                        // In a real implementation, this would be where we'd transform the code
                        System.out.println("DETECTED: ScriptResult instantiation at line " + 
                            methodCall.getRange().map(r -> Integer.toString(r.begin.line)).orElse("unknown"));
                    }
                }
            }
        }
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Detect and note ScriptResult imports
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                System.out.println("DETECTED: Obsolete ScriptResult import at line " + 
                    importDecl.getRange().map(r -> Integer.toString(r.begin.line)).orElse("unknown"));
            }
        }
        
        @Override
        public void visit(VariableDeclarator variableDeclarator, Void arg) {
            super.visit(variableDeclarator, arg);
            
            // Detect ScriptResult variable declarations
            String varType = variableDeclarator.getType().toString();
            if (varType.contains("ScriptResult")) {
                System.out.println("DETECTED: ScriptResult variable '" + 
                    variableDeclarator.getNameAsString() + "' at line " +
                    variableDeclarator.getRange().map(r -> Integer.toString(r.begin.line)).orElse("unknown"));
            }
        }
    }
}