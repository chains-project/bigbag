package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Transforming files in: " + sourceDir.toAbsolutePath());
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> transformFile(p));
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) {
        try {
            System.out.println("Processing: " + filePath);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            
            // Track if we modify the file
            boolean[] modified = new boolean[]{false};
            
            // 1. Transform imports
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ImportDeclaration n, Void arg) {
                    if (n.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        System.out.println("  - Removing old ScriptResult import");
                        modified[0] = true;
                        // Remove the import since we won't use ScriptResult class anymore
                        return null;
                    }
                    return n;
                }
            }, null);
            
            // 2. Transform ScriptResult usage patterns in multiple passes
            
            // Pass 1: Transform all new ScriptResult(expr) constructors
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ObjectCreationExpr n, Void arg) {
                    // Check if this is a ScriptResult constructor call
                    if (n.getType().asString().equals("ScriptResult") && n.getArguments().size() == 1) {
                        System.out.println("  - Transforming ScriptResult constructor at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                        modified[0] = true;
                        
                        Expression argExpr = n.getArgument(0);
                        // new ScriptResult(expr) -> expr != null ? expr.toString() : null
                        ConditionalExpr conditional = new ConditionalExpr(
                            new BinaryExpr(
                                argExpr.clone(),
                                new NullLiteralExpr(),
                                BinaryExpr.Operator.NOT_EQUALS
                            ),
                            new MethodCallExpr(argExpr.clone(), "toString"),
                            new NullLiteralExpr()
                        );
                        return conditional;
                    }
                    return n;
                }
            }, null);
            
            // Pass 2: Remove all getJavaScriptResult() method calls
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(MethodCallExpr n, Void arg) {
                    // Check if this is a getJavaScriptResult() call
                    if (n.getNameAsString().equals("getJavaScriptResult")) {
                        System.out.println("  - Removing getJavaScriptResult() call at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                        modified[0] = true;
                        
                        // If it has a scope (e.g., expr.getJavaScriptResult()), replace with just the scope
                        // The scope could be:
                        // 1. A transformed conditional expression (from new ScriptResult(...))
                        // 2. A variable that was originally ScriptResult type (now String)
                        // 3. Other expression
                        if (n.getScope().isPresent()) {
                            return n.getScope().get();
                        } else {
                            // No scope - shouldn't happen for getJavaScriptResult()
                            return n;
                        }
                    }
                    return n;
                }
            }, null);
            
            // Pass 3: Change ScriptResult type references to String
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ClassOrInterfaceType n, Void arg) {
                    // Check if this is a ScriptResult type reference
                    if (n.getNameAsString().equals("ScriptResult")) {
                        System.out.println("  - Changing ScriptResult type to String at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                        modified[0] = true;
                        return new ClassOrInterfaceType("String");
                    }
                    return n;
                }
            }, null);
            
            if (modified[0]) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("  - File modified");
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}