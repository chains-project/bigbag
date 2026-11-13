package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findAllJavaFiles(sourceDir);
            for (Path file : javaFiles) {
                transformFile(file);
            }
            System.out.println("Transformation complete. Processed " + javaFiles.size() + " files.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static void transformFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + file)
        );
        
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                // Remove the import since we won't use ScriptResult class anymore
                importDecl.remove();
                modified = true;
            }
        }
        
        // Transform code using visitor pattern
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        cu.accept(transformer, null);
        modified = modified || transformer.wasModified();
        
        if (modified) {
            // Save transformed file
            String transformedCode = cu.toString(new DefaultPrinterConfiguration());
            Files.write(file, transformedCode.getBytes());
            System.out.println("Transformed: " + file);
        }
    }
    
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check for new ScriptResult(...)
            if (n.getType().asString().equals("ScriptResult")) {
                // Replace new ScriptResult(result) with result
                if (n.getArguments().size() == 1) {
                    Node argExpr = n.getArguments().get(0);
                    modified = true;
                    return argExpr.clone();
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for .getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Replace scriptResult.getJavaScriptResult() with scriptResult
                Node scope = n.getScope().orElse(null);
                if (scope != null) {
                    modified = true;
                    return scope.clone();
                }
            }
            return super.visit(n, arg);
        }
    }
}