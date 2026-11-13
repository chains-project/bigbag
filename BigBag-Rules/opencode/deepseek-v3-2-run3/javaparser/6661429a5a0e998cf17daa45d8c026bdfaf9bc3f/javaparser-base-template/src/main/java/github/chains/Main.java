package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile));
            
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                // Remove the import since we won't use ScriptResult anymore
                importDecl.remove();
                modified = true;
            }
        }
        
        // Transform code using visitor pattern
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        cu.accept(transformer, null);
        if (transformer.modified) {
            modified = true;
        }
        
        if (modified) {
            Files.write(javaFile, cu.toString().getBytes());
            System.out.println("Transformed: " + javaFile);
        }
    }
    
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a ScriptResult constructor call
            if (n.getType().getNameAsString().equals("ScriptResult")) {
                NodeList<Expression> arguments = n.getArguments();
                if (arguments.size() == 1) {
                    Expression argExpr = arguments.get(0);
                    // Transform new ScriptResult(object) to object
                    // The object is already the result we need
                    n.replace(argExpr);
                    modified = true;
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a getJavaScriptResult() call
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Remove the method call and return the scope
                Expression scope = n.getScope().orElse(null);
                if (scope != null) {
                    // Replace the method call with its scope
                    n.replace(scope);
                    modified = true;
                }
            }
            return super.visit(n, arg);
        }
    }
}