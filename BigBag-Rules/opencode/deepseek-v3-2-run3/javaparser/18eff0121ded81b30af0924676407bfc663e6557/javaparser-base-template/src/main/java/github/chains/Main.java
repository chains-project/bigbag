package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing the breaking change where:
 * 1. com.gargoylesoftware.htmlunit.ScriptResult class was removed
 * 2. PageObject.executeScript() now returns Object directly instead of ScriptResult
 * 3. ScriptResult.getJavaScriptResult() is no longer needed
 * 
 * Pattern transformations:
 * 1. Remove import: import com.gargoylesoftware.htmlunit.ScriptResult;
 * 2. Replace: ScriptResult var = new ScriptResult(expr); -> Object var = expr;
 * 3. Replace: new ScriptResult(expr).getJavaScriptResult() -> expr
 * 4. Replace: var.getJavaScriptResult() -> var (where var is of type ScriptResult)
 */
public class Main {
    
    // Configuration - should be parameterized for different breaking changes
    private static final String OLD_CLASS_TO_REMOVE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_CLASS_SIMPLE_NAME = "ScriptResult";
    private static final String METHOD_TO_REMOVE = "getJavaScriptResult";
    private static final String REPLACEMENT_TYPE = "Object";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Transformation for: " + OLD_CLASS_TO_REMOVE + " -> " + REPLACEMENT_TYPE);
            System.err.println("Removes method: " + METHOD_TO_REMOVE + "()");
            System.exit(1);
        }
        
        Path sourcePath = Paths.get(args[0]);
        System.out.println("Processing: " + sourcePath.toAbsolutePath());
        
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(sourcePath)) {
            javaFiles = walk
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalTransformations = 0;
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                CompilationUnit cu = StaticJavaParser.parse(content);
                
                ScriptResultTransformer transformer = new ScriptResultTransformer();
                cu.accept(transformer, null);
                
                if (transformer.transformations > 0) {
                    filesModified++;
                    totalTransformations += transformer.transformations;
                    
                    String transformedCode = cu.toString();
                    Files.write(javaFile, transformedCode.getBytes(StandardCharsets.UTF_8));
                    
                    System.out.println("  Modified: " + javaFile.getFileName() + 
                                      " (" + transformer.transformations + " transformations)");
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private int transformations = 0;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            if (importName.equals(OLD_CLASS_TO_REMOVE)) {
                importDecl.remove();
                transformations++;
                return null; // Removed node
            }
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Check for "new ScriptResult(...)"
            if (expr.getType().getNameAsString().equals(OLD_CLASS_SIMPLE_NAME)) {
                NodeList<Expression> arguments = expr.getArguments();
                if (!arguments.isEmpty()) {
                    Expression firstArg = arguments.get(0);
                    
                    // Check parent to see what we need to do
                    Node parent = expr.getParentNode().orElse(null);
                    
                    if (parent instanceof VariableDeclarationExpr) {
                        // Case: ScriptResult var = new ScriptResult(expr);
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) parent;
                        if (varDecl.getVariable(0).getType().toString().equals(OLD_CLASS_SIMPLE_NAME)) {
                            // Replace type with Object and remove constructor call
                            varDecl.getVariable(0).setType(REPLACEMENT_TYPE);
                            varDecl.getVariable(0).setInitializer(firstArg);
                            transformations++;
                            // Don't visit children since we've transformed this node
                            return null;
                        }
                    } else if (parent instanceof MethodCallExpr) {
                        // Case: new ScriptResult(expr).getJavaScriptResult()
                        MethodCallExpr methodCall = (MethodCallExpr) parent;
                        if (methodCall.getNameAsString().equals(METHOD_TO_REMOVE)) {
                            // Replace the entire method call with just the argument
                            Node grandParent = methodCall.getParentNode().orElse(null);
                            if (grandParent != null) {
                                methodCall.replace(firstArg);
                                transformations++;
                                // Return the argument to continue visiting it
                                return firstArg;
                            }
                        }
                    }
                }
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            // Check for calls to getJavaScriptResult()
            if (expr.getNameAsString().equals(METHOD_TO_REMOVE)) {
                // Remove the method call, keep the scope
                if (expr.getScope().isPresent()) {
                    Expression scope = expr.getScope().get();
                    expr.replace(scope);
                    transformations++;
                    // Return the scope to continue visiting it
                    return scope;
                }
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(VariableDeclarationExpr expr, Void arg) {
            // Also check for variable declarations of type ScriptResult without initializer
            // This handles cases like: ScriptResult result;
            if (expr.getVariable(0).getType().toString().equals(OLD_CLASS_SIMPLE_NAME)) {
                expr.getVariable(0).setType(REPLACEMENT_TYPE);
                transformations++;
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override 
        public Node visit(ClassOrInterfaceType type, Void arg) {
            // Handle type references in other contexts (method parameters, field types, etc.)
            if (type.getNameAsString().equals(OLD_CLASS_SIMPLE_NAME)) {
                type.setName(REPLACEMENT_TYPE);
                transformations++;
            }
            return (Node) super.visit(type, arg);
        }
    }
}