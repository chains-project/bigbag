package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing breaking changes when 
 * com.gargoylesoftware.htmlunit.ScriptResult is removed from dependencies.
 * 
 * This transformation handles:
 * 1. Removing imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replacing new ScriptResult(expr) with expr in all contexts
 * 3. Removing .getJavaScriptResult() method calls
 * 4. Changing ScriptResult variable declarations to Object
 * 
 * The rule is generic and can be applied to any Java project affected by this
 * breaking change by specifying the source directory path.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Applies transformation to fix removal of com.gargoylesoftware.htmlunit.ScriptResult");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying transformation for com.gargoylesoftware.htmlunit.ScriptResult removal...");
        System.out.println("Source directory: " + sourceDir);
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> processFile(p.toFile()));
        }
        
        System.out.println("Transformation completed.");
    }
    
    private static void processFile(File file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new FileInputStream(file)).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + file);
                return;
            }
            
            boolean modified = false;
            
            // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportDeclaration importDecl = imports.get(i);
                String importName = importDecl.getNameAsString();
                if (importName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    imports.remove(i);
                    modified = true;
                    System.out.println("  Removed import: " + importName);
                }
            }
            
            // Apply transformation multiple times to handle nested patterns
            boolean changed;
            do {
                changed = false;
                ScriptResultTransformer transformer = new ScriptResultTransformer();
                cu.accept(transformer, null);
                if (transformer.modified) {
                    changed = true;
                    modified = true;
                }
            } while (changed);
            
            if (modified) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(cu.toString().getBytes());
                }
                System.out.println("Modified: " + file);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms ScriptResult usage patterns.
     * 
     * Patterns handled:
     * 1. new ScriptResult(expr) -> expr (in all contexts)
     * 2. .getJavaScriptResult() method call removal
     * 3. ScriptResult type to Object type in variable declarations
     */
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Replace new ScriptResult(expr) with expr
            if (n.getType().asString().equals("ScriptResult") && n.getArguments().size() == 1) {
                modified = true;
                return n.getArguments().get(0).clone();
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Remove .getJavaScriptResult() method calls
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                modified = true;
                // Replace expr.getJavaScriptResult() with expr
                if (n.getScope().isPresent()) {
                    return n.getScope().get().clone();
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Change ScriptResult type to Object in variable declarations
            String typeName = n.getVariable(0).getType().asString();
            if (typeName.equals("ScriptResult")) {
                modified = true;
                n.getVariable(0).setType(new ClassOrInterfaceType("Object"));
            }
            return super.visit(n, arg);
        }
    }
}