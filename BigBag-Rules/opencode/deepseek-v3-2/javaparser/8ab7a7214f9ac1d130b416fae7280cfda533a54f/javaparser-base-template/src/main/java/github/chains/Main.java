package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: " + sourceDir + " is not a valid directory");
            System.exit(1);
        }
        
        try {
            transformSourceFiles(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformSourceFiles(Path sourceDir) throws IOException {
        Files.walkFileTree(sourceDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
            new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        transformFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
    }
    
    private static void transformFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + file));
        
        boolean modified = false;
        
        // Remove com.gargoylesoftware.htmlunit.ScriptResult imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration importDecl = imports.get(i);
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                imports.remove(i);
                modified = true;
                System.out.println("Removed import: " + importDecl.getNameAsString() + " from " + file);
            }
        }
        
        // Apply transformation to remove ScriptResult wrappers
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        cu.accept(transformer, null);
        if (transformer.modified) {
            modified = true;
        }
        
        if (modified) {
            Files.writeString(file, cu.toString());
            System.out.println("Transformed: " + file);
        }
    }
    
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new ScriptResult(...) expression
            if (n.getType().asString().equals("ScriptResult")) {
                // Get the argument (should be the result from executeScript)
                if (n.getArguments().size() == 1) {
                    Expression argument = n.getArguments().get(0);
                    modified = true;
                    // Replace new ScriptResult(expr) with just expr
                    return argument;
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a .getJavaScriptResult() call
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the scope is a ScriptResult object
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    // Replace the entire expression with just the scope
                    // This handles: scriptResult.getJavaScriptResult() -> scriptResult
                    modified = true;
                    return scope;
                }
            }
            return super.visit(n, arg);
        }
    }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a .getJavaScriptResult() call
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the scope is a ScriptResult object
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    // Replace the entire expression with just the scope
                    // This handles: scriptResult.getJavaScriptResult() -> scriptResult
                    modified = true;
                    return scope;
                }
            }
            return super.visit(n, arg);
        }
    }
}