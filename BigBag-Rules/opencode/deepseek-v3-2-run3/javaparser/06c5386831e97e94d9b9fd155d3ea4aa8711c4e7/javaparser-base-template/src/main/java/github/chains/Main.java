package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        Path startDir = Paths.get(sourceDir);
        if (!Files.exists(startDir)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(startDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (InputStream in = Files.newInputStream(javaFile)) {
            cu = parser.parse(in).getResult().orElse(null);
        }
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        HtmlUnitScriptResultTransformer transformer = new HtmlUnitScriptResultTransformer();
        boolean modified = false;
        
        // Remove imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration importDecl = imports.get(i);
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                imports.remove(i);
                modified = true;
                System.out.println("Removed import from: " + javaFile);
            }
        }
        
        // Transform AST
        transformer.visit(cu, null);
        if (transformer.wasModified()) {
            modified = true;
        }
        
        if (modified) {
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(javaFile))) {
                out.println(cu.toString());
            }
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    private static class HtmlUnitScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children to transform nested expressions
            super.visit(n, arg);
            
            // Check if this is .getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Check if we're calling on a ScriptResult instance
                Expression scope = n.getScope().orElse(null);
                if (scope != null) {
                    // Check if scope is new ScriptResult(...)
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                        if (creation.getType().asString().equals("ScriptResult")) {
                            // Replace new ScriptResult(expr).getJavaScriptResult() with expr
                            NodeList<Expression> args = creation.getArguments();
                            if (args.size() == 1) {
                                modified = true;
                                return args.get(0);
                            }
                        }
                    } else {
                        // Variable or other expression of type ScriptResult
                        // Just remove .getJavaScriptResult() call
                        modified = true;
                        return scope;
                    }
                }
            }
            return n;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is new ScriptResult(...)
            if (n.getType().asString().equals("ScriptResult")) {
                // We'll handle this in visit(MethodCallExpr) if followed by .getJavaScriptResult()
                // Otherwise, we need to replace it here
                // First visit children
                super.visit(n, arg);
                
                // Check if parent is already a MethodCallExpr for .getJavaScriptResult()
                // If not, replace with argument
                NodeList<Expression> args = n.getArguments();
                if (args.size() == 1) {
                    modified = true;
                    return args.get(0);
                }
                return n;
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Replace ScriptResult type declarations with Object
            if (n.getNameAsString().equals("ScriptResult")) {
                modified = true;
                return new ClassOrInterfaceType("Object");
            }
            return super.visit(n, arg);
        }
    }
}