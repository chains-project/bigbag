package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
            
            boolean modified = false;
            
            // Remove com.gargoylesoftware.htmlunit.ScriptResult imports
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportDeclaration importDecl = imports.get(i);
                if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    importDecl.remove();
                    modified = true;
                }
            }
            
            // Apply transformation
            ScriptResultModifier modifier = new ScriptResultModifier();
            CompilationUnit modifiedCu = (CompilationUnit) modifier.visit(cu, null);
            
            if (modified || modifier.wasModified()) {
                Files.write(file, modifiedCu.toString().getBytes());
                System.out.println("Modified: " + file);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    private static class ScriptResultModifier extends ModifierVisitor<Void> {
        private boolean modified = false;
        private Map<String, String> variableReplacements = new HashMap<>();
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(BlockStmt n, Void arg) {
            // Reset variable tracking for each block
            variableReplacements.clear();
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Track ScriptResult creation to handle variable assignments
            if (n.getType().asString().equals("ScriptResult")) {
                NodeList<Expression> args = n.getArguments();
                if (args.size() == 1 && n.getParentNode().isPresent()) {
                    // This will be handled in visit(MethodCallExpr) for method chains
                    // or we need to track variable assignments
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children
            super.visit(n, arg);
            
            // Pattern 1: new ScriptResult(result).getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult") && 
                n.getScope().isPresent() && 
                n.getScope().get() instanceof ObjectCreationExpr) {
                
                ObjectCreationExpr creation = (ObjectCreationExpr) n.getScope().get();
                if (creation.getType().asString().equals("ScriptResult")) {
                    NodeList<Expression> args = creation.getArguments();
                    if (args.size() == 1) {
                        // Replace new ScriptResult(result).getJavaScriptResult() with result
                        modified = true;
                        return args.get(0);
                    }
                }
            }
            
            // Pattern 2: variable.getJavaScriptResult() - need to track variable assignments
            // For simplicity in this generic rule, we'll handle common patterns
            
            return n;
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Could track variable assignments here
            return super.visit(n, arg);
        }
    }
}