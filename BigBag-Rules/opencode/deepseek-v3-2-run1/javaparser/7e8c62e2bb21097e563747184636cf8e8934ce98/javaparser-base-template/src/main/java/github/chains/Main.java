package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            transformDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                .filter(imp -> imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult"))
                .collect(Collectors.toList());
            
            if (!importsToRemove.isEmpty()) {
                importsToRemove.forEach(cu::remove);
                modified = true;
                System.out.println("Removed ScriptResult import from: " + filePath);
            }
            
            // Apply transformation to replace new ScriptResult(...) patterns
            ScriptResultTransformer transformer = new ScriptResultTransformer();
            Visitable visited = transformer.visit(cu, null);
            
            if (transformer.isModified()) {
                modified = true;
            }
            
            if (modified) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
            
            if (modified) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is new ScriptResult(...)
            if (n.getType().toString().equals("ScriptResult")) {
                // Check if this is followed by .getJavaScriptResult()
                Optional<Node> parent = n.getParentNode();
                if (parent.isPresent() && parent.get() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) parent.get();
                    if (methodCall.getName().getIdentifier().equals("getJavaScriptResult")) {
                        // Replace new ScriptResult(expr).getJavaScriptResult() with expr
                        if (n.getArguments().size() == 1) {
                            Expression argument = n.getArguments().get(0);
                            Node grandParent = methodCall.getParentNode().orElse(null);
                            if (grandParent != null) {
                                // Replace the MethodCallExpr with just the argument
                                methodCall.replace(argument);
                                modified = true;
                                return argument;
                            }
                        }
                    }
                } else {
                    // Replace new ScriptResult(expr) with expr
                    if (n.getArguments().size() == 1) {
                        Expression argument = n.getArguments().get(0);
                        n.replace(argument);
                        modified = true;
                        return argument;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Handle cases like: ScriptResult scriptResult = new ScriptResult(result);
            // We need to check the type and replace with appropriate type
            
            // First visit children
            super.visit(n, arg);
            
            // Check if variable type is ScriptResult
            if (n.getVariables().size() == 1) {
                com.github.javaparser.ast.body.VariableDeclarator var = n.getVariables().get(0);
                if (var.getType().toString().equals("ScriptResult")) {
                    // Check if initializer is ObjectCreationExpr for ScriptResult
                    Optional<Expression> initializer = var.getInitializer();
                    if (initializer.isPresent() && initializer.get() instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) initializer.get();
                        if (creation.getType().toString().equals("ScriptResult")) {
                            // Replace ScriptResult type with Object and keep the expression
                            var.setType(new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "Object"));
                            
                            // Replace the initializer with just the argument
                            if (creation.getArguments().size() == 1) {
                                var.setInitializer(creation.getArguments().get(0));
                                modified = true;
                            }
                        }
                    }
                }
            }
            
            return n;
        }
    }
}