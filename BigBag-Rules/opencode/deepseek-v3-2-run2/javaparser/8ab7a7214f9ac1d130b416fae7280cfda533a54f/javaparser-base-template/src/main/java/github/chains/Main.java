package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try {
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
            
            ScriptResultTransformation transformation = new ScriptResultTransformation();
            cu.accept(transformation, null);
            
            if (transformation.wasModified()) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Transformed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ScriptResultTransformation extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Pattern: new ScriptResult(result).getJavaScriptResult()
            if (n.getName().asString().equals("getJavaScriptResult")) {
                Expression scope = n.getScope().orElse(null);
                if (scope instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                    if (creation.getType().asString().equals("ScriptResult")) {
                        List<Expression> arguments = creation.getArguments();
                        if (arguments.size() == 1) {
                            // Replace the entire method call with just the result argument
                            modified = true;
                            return arguments.get(0);
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Pattern: ScriptResult scriptResult = new ScriptResult(result)
            if (n.getVariable(0).getType().asString().equals("ScriptResult")) {
                Expression initializer = n.getVariable(0).getInitializer().orElse(null);
                if (initializer instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) initializer;
                    if (creation.getType().asString().equals("ScriptResult")) {
                        List<Expression> arguments = creation.getArguments();
                        if (arguments.size() == 1) {
                            // Replace ScriptResult variable with Object variable
                            n.getVariable(0).setType("Object");
                            modified = true;
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Remove import of ScriptResult
            if (n.getName().asString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                modified = true;
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
    }
}