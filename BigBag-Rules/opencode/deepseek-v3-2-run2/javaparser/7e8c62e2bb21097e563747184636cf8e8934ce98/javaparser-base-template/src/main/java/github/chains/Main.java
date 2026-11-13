package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule to fix breaking changes from acceptance-test-harness dependency update.
 * 
 * This transformation handles the removal of com.gargoylesoftware.htmlunit.ScriptResult:
 * - Removes imports of com.gargoylesoftware.htmlunit.ScriptResult
 * - Replaces "new ScriptResult(result)" with direct use of "result"
 * - Replaces "scriptResult.getJavaScriptResult()" with direct use of the result object
 * 
 * This is a generic transformation that can be applied to any project affected by this breaking change.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        try {
            transformProject(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            boolean modified = false;
            
            // Remove ScriptResult imports
            List<ImportDeclaration> imports = cu.getImports();
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportDeclaration importDecl = imports.get(i);
                if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    cu.remove(importDecl);
                    modified = true;
                }
            }
            
            // Apply transformation to remove ScriptResult usage
            ScriptResultTransformer transformer = new ScriptResultTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                modified = true;
            }
            
            if (modified) {
                Files.writeString(javaFile, cu.toString());
                System.out.println("Transformed: " + javaFile);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
    
        /**
         * Visitor that transforms ScriptResult usage patterns.
         * This is a generic transformation that:
         * 1. Replaces "new ScriptResult(result)" with "result"
         * 2. Replaces "scriptResult.getJavaScriptResult()" with "scriptResult"
         * 3. Removes variable declarations and references to ScriptResult type
         */
        private static class ScriptResultTransformer extends ModifierVisitor<Void> {
            private boolean modified = false;
            
            public boolean wasModified() {
                return modified;
            }
            
            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                // Check if this is "new ScriptResult(result)"
                if (n.getType().asString().equals("ScriptResult") && n.getArguments().size() == 1) {
                    // Replace "new ScriptResult(result)" with just "result"
                    Node replacement = n.getArgument(0);
                    modified = true;
                    return replacement;
                }
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                // Check if this is "scriptResult.getJavaScriptResult()"
                if (n.getNameAsString().equals("getJavaScriptResult") && n.getScope().isPresent()) {
                    // Replace "scriptResult.getJavaScriptResult()" with "scriptResult"
                    Node replacement = n.getScope().get();
                    modified = true;
                    return replacement;
                }
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(VariableDeclarationExpr n, Void arg) {
                // Check if this is a variable declaration with ScriptResult type
                for (com.github.javaparser.ast.body.VariableDeclarator declarator : n.getVariables()) {
                    if (declarator.getType().asString().equals("ScriptResult")) {
                        // Replace ScriptResult type with Object type
                        declarator.setType(StaticJavaParser.parseType("Object"));
                        modified = true;
                    }
                }
                return super.visit(n, arg);
            }
        }
}