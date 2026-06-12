package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try {
            processDirectory(sourcePath.toFile());
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(File directory) throws FileNotFoundException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + directory);
        }
        
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".java"));
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            processFile(file);
        }
        
        File[] subdirs = directory.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                processDirectory(subdir);
            }
        }
    }
    
    private static void processFile(File file) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(file).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + file);
            return;
        }
        
        CompilationUnit cu = cuOpt.get();
        
        boolean modified = false;
        
        modified |= removeHtmlUnitScriptResultImport(cu);
        
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        CompilationUnit transformed = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.isModified() || modified) {
            System.out.println("Modified: " + file);
            try (PrintWriter out = new PrintWriter(file)) {
                out.print(transformed.toString());
            } catch (Exception e) {
                System.err.println("Failed to save file: " + file);
                e.printStackTrace();
            }
        }
    }
    
    private static boolean removeHtmlUnitScriptResultImport(CompilationUnit cu) {
        List<ImportDeclaration> imports = cu.getImports();
        boolean removed = imports.removeIf(importDecl -> {
            String importName = importDecl.getNameAsString();
            return importName.equals("com.gargoylesoftware.htmlunit.ScriptResult");
        });
        return removed;
    }
    
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().asString().equals("ScriptResult")) {
                List<Expression> args = n.getArguments();
                if (args.size() == 1) {
                    modified = true;
                    return args.get(0);
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                Expression scope = n.getScope().orElse(null);
                if (scope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope;
                    modified = true;
                    return nameExpr;
                }
            }
            return super.visit(n, arg);
        }
    }
}