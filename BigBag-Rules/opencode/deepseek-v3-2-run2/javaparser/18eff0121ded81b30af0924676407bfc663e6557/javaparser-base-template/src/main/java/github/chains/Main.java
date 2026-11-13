package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule to fix compilation errors caused by removal of ScriptResult class
 * from acceptance-test-harness dependency update.
 * 
 * This transformation:
 * 1. Removes ScriptResult import declarations
 * 2. Replaces ScriptResult constructor calls with direct object usage
 * 3. Replaces getJavaScriptResult() method calls with direct object usage
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        return Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return false;
            }
            
            ScriptResultTransformer transformer = new ScriptResultTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasTransformed()) {
                Files.writeString(javaFile, cu.toString());
                System.out.println("Transformed: " + javaFile);
                return true;
            }
            
            return false;
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Visitor that transforms ScriptResult usage patterns
     */
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Remove ScriptResult imports
            if (n.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                transformed = true;
                return null; // Remove the import
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(VariableDeclarationExpr n, Void arg) {
            // Pattern: ScriptResult scriptResult = new ScriptResult(result)
            if (n.getVariables().size() == 1) {
                com.github.javaparser.ast.body.VariableDeclarator var = n.getVariables().get(0);
                if (var.getType().asString().equals("ScriptResult") && 
                    var.getInitializer().isPresent() &&
                    var.getInitializer().get() instanceof ObjectCreationExpr) {
                    
                    ObjectCreationExpr creationExpr = (ObjectCreationExpr) var.getInitializer().get();
                    if (creationExpr.getType().asString().equals("ScriptResult")) {
                        // Extract the argument passed to ScriptResult constructor
                        if (creationExpr.getArguments().size() == 1) {
                            Expression resultArg = creationExpr.getArguments().get(0);
                            // Replace with direct variable declaration
                            var.setType(new com.github.javaparser.ast.type.ClassOrInterfaceType("Object"));
                            var.setInitializer(resultArg);
                            transformed = true;
                        }
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // Pattern: scriptResult.getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult") && 
                n.getScope().isPresent()) {
                
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope;
                    // Replace method call with just the variable name
                    transformed = true;
                    return nameExpr;
                }
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            // Pattern: new ScriptResult(result).getJavaScriptResult()
            if (n.getType().asString().equals("ScriptResult") && 
                n.getArguments().size() == 1) {
                
                // Check if this is part of a method call chain
                Node parent = n.getParentNode().orElse(null);
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) parent;
                    if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                        // Replace entire expression with just the constructor argument
                        transformed = true;
                        return n.getArguments().get(0);
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }
    }
}