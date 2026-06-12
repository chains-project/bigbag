package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
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
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        boolean modified = false;
        
        modified |= transformer.removeHtmlUnitImports(cu);
        modified |= transformer.transformScriptResultUsage(cu);
        
        if (modified) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String transformedCode = printer.print(cu);
            Files.write(javaFile, transformedCode.getBytes());
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private static final String HTML_UNIT_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
        
        public boolean removeHtmlUnitImports(CompilationUnit cu) {
            NodeList<ImportDeclaration> imports = cu.getImports();
            boolean modified = false;
            
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportDeclaration imp = imports.get(i);
                if (imp.getNameAsString().equals(HTML_UNIT_SCRIPT_RESULT)) {
                    imports.remove(i);
                    modified = true;
                }
            }
            
            return modified;
        }
        
        public boolean transformScriptResultUsage(CompilationUnit cu) {
            ScriptResultUsageVisitor visitor = new ScriptResultUsageVisitor();
            visitor.visit(cu, null);
            return visitor.isModified();
        }
    }
    
    static class ScriptResultUsageVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().asString().equals("ScriptResult")) {
                NodeList<Expression> arguments = n.getArguments();
                if (arguments.size() == 1) {
                    Expression argExpr = arguments.get(0);
                    
                    ObjectCreationExpr parent = (ObjectCreationExpr) n.getParentNode().orElse(null);
                    if (parent != null && parent.getType().asString().equals("ScriptResult")) {
                        return argExpr;
                    }
                    
                    MethodCallExpr enclosingCall = findEnclosingGetJavaScriptResultCall(n);
                    if (enclosingCall != null && enclosingCall.getNameAsString().equals("getJavaScriptResult")) {
                        modified = true;
                        return argExpr;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                Expression scope = n.getScope().orElse(null);
                if (scope instanceof ObjectCreationExpr) {
                    ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope;
                    if (objectCreation.getType().asString().equals("ScriptResult")) {
                        NodeList<Expression> args = objectCreation.getArguments();
                        if (args.size() == 1) {
                            modified = true;
                            return args.get(0);
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            for (com.github.javaparser.ast.body.VariableDeclarator var : n.getVariables()) {
                if (var.getType().asString().equals("ScriptResult")) {
                    Expression initializer = var.getInitializer().orElse(null);
                    if (initializer instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objectCreation = (ObjectCreationExpr) initializer;
                        NodeList<Expression> args = objectCreation.getArguments();
                        if (args.size() == 1) {
                            modified = true;
                            var.setType("Object");
                            var.setInitializer(args.get(0));
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private MethodCallExpr findEnclosingGetJavaScriptResultCall(ObjectCreationExpr n) {
            com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
            while (parent != null) {
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr call = (MethodCallExpr) parent;
                    if (call.getNameAsString().equals("getJavaScriptResult")) {
                        return call;
                    }
                }
                parent = parent.getParentNode().orElse(null);
            }
            return null;
        }
    }
}