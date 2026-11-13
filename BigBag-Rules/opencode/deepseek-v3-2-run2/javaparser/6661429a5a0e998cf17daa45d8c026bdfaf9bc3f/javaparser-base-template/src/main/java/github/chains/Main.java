package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile.toFile())) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            CompilationUnit cu = new JavaParser().parse(input).getResult().orElse(null);
            if (cu == null) {
                return false;
            }
            
            boolean modified = false;
            
            // Transform imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    importDecl.setName("org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult");
                    modified = true;
                }
            }
            
            // Transform constructor calls and method calls
            ScriptResultTransformer transformer = new ScriptResultTransformer();
            cu.accept(transformer, null);
            if (transformer.wasModified()) {
                modified = true;
            }
            
            if (modified) {
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(cu.toString().getBytes());
                }
                System.out.println("Modified: " + file.getPath());
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + file.getPath() + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Transform: new ScriptResult(result) -> new ScriptResult(result.toString())
            if (n.getType().asString().equals("ScriptResult") && n.getArguments().size() == 1) {
                Expression argument = n.getArguments().get(0);
                if (!argument.toString().endsWith(".toString()")) {
                    n.getArguments().set(0, new MethodCallExpr(argument, "toString"));
                    modified = true;
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Transform: scriptResult.getJavaScriptResult().toString() -> scriptResult.toString()
            if (n.getNameAsString().equals("toString")) {
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr innerCall = (MethodCallExpr) scope;
                        if (innerCall.getNameAsString().equals("getJavaScriptResult")) {
                            // Replace the entire chain with just the scope of getJavaScriptResult()
                            if (innerCall.getScope().isPresent()) {
                                Expression scriptResultVar = innerCall.getScope().get();
                                return new MethodCallExpr(scriptResultVar, "toString");
                            }
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}