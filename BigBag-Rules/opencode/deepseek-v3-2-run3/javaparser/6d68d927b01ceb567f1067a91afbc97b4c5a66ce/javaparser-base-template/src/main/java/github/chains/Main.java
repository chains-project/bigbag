package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processDirectory(File directory) throws FileNotFoundException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory.getPath());
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                processJavaFile(file);
            }
        }
    }

    private static void processJavaFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> optionalCu = parser.parse(javaFile).getResult();
        if (!optionalCu.isPresent()) {
            System.err.println("Failed to parse: " + javaFile.getPath());
            return;
        }
        CompilationUnit cu = optionalCu.get();
        // Create visitor to transform ScriptResult usage
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        cu.accept(transformer, null);
        // Write back the modified file
        try {
            Files.write(javaFile.toPath(), cu.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            System.out.println("Processed: " + javaFile.getPath());
        } catch (Exception e) {
            System.err.println("Failed to save: " + javaFile.getPath() + " - " + e.getMessage());
        }
    }

    private static class ScriptResultTransformer extends ModifierVisitor<Void> {

        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Remove import of com.gargoylesoftware.htmlunit.ScriptResult
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                // Remove this import
                return null;
            }
            return (Node) super.visit(importDecl, arg);
        }

        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Check if this is a new ScriptResult(...) expression
            String typeName = expr.getTypeAsString();
            if ("ScriptResult".equals(typeName)) {
                // Check if we have exactly one argument
                NodeList<Expression> arguments = expr.getArguments();
                if (arguments.size() == 1) {
                    Expression argExpr = arguments.get(0);
                    // Check if parent is MethodCallExpr with getJavaScriptResult()
                    // If yes, it will be handled by visit(MethodCallExpr)
                    // Otherwise, replace new ScriptResult(x) with just x
                    Optional<Node> parent = expr.getParentNode();
                    if (parent.isPresent() && parent.get() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) parent.get();
                        if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                            // Let visit(MethodCallExpr) handle this case
                            return (Node) super.visit(expr, arg);
                        }
                    }
                    // Standalone new ScriptResult(x), replace with x
                    return (Node) argExpr;
                }
            }
            return (Node) super.visit(expr, arg);
        }

        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            // Check for .getJavaScriptResult() calls
            if (expr.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the scope is a ScriptResult object creation or variable
                Optional<Expression> scope = expr.getScope();
                if (scope.isPresent()) {
                    Expression scopeExpr = scope.get();
                    // Check if scope is ObjectCreationExpr of ScriptResult
                    if (scopeExpr instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objectCreation = (ObjectCreationExpr) scopeExpr;
                        if ("ScriptResult".equals(objectCreation.getTypeAsString())) {
                            // For new ScriptResult(x).getJavaScriptResult(), replace entire expression with x
                            NodeList<Expression> arguments = objectCreation.getArguments();
                            if (arguments.size() == 1) {
                                return (Node) arguments.get(0);
                            }
                        }
                    }
                    // For other cases like variable.getJavaScriptResult(), replace with just the variable
                    // The variable type will be changed from ScriptResult to Object by visit(ClassOrInterfaceType)
                    return (Node) scopeExpr;
                } else {
                    // No scope - static method call like ScriptResult.getJavaScriptResult()?
                    // Not likely, but handle by removing
                    return null;
                }
            }
            return (Node) super.visit(expr, arg);
        }

        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            // Change ScriptResult type references to Object
            if (type.getNameAsString().equals("ScriptResult")) {
                // Replace ScriptResult type with Object
                type.setName("Object");
            }
            return (Node) super.visit(type, arg);
        }
    }
}
