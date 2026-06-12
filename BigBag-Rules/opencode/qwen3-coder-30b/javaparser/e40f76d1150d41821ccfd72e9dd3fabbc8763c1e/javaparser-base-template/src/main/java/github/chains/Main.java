package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);

        try {
            SourceRoot root = new SourceRoot(sourcePath);
            
            // Process all Java files in the directory
            root.parse("", new SourceRoot.Callback() {
                @Override
                public SourceRoot.Callback.Result process(Path localPath, CompilationUnit cu) {
                    if (cu != null) {
                        // Apply the transformation to replace ScriptResult usage
                        new ScriptResultReplacementVisitor().visit(cu, null);
                    }
                    return SourceRoot.Callback.Result.SAVE;
                }
            });
            
            // Save all modified files
            root.saveAll();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Visitor to find and replace ScriptResult usage patterns
     */
    private static class ScriptResultReplacementVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a call to getJavaScriptResult() on a ScriptResult object
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Find the object being called on
                Expression scope = n.getScope().orElse(null);
                if (scope != null && scope instanceof MethodCallExpr) {
                    MethodCallExpr scopeMethod = (MethodCallExpr) scope;
                    if (scopeMethod.getNameAsString().equals("new ScriptResult")) {
                        // Replace with direct usage of the result variable
                        // This pattern: new ScriptResult(result).getJavaScriptResult()
                        // Should become: result
                        NodeList<Expression> args = scopeMethod.getArguments();
                        if (args.size() == 1) {
                            Expression resultExpr = args.get(0);
                            n.replace(resultExpr);
                        }
                    }
                }
            }
        }
    }
}