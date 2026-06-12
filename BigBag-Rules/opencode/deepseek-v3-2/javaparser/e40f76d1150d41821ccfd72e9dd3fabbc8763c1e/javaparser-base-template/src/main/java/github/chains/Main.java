package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(File directory) throws FileNotFoundException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + directory.getPath());
        }
        
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        for (File javaFile : javaFiles) {
            processFile(javaFile);
        }
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    private static void processFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + javaFile.getPath());
            return;
        }
        
        CompilationUnit cu = cuOpt.get();
        boolean modified = false;
        
        // Remove ScriptResult imports
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                importsToRemove.add(importDecl);
                modified = true;
            }
        }
        cu.getImports().removeAll(importsToRemove);
        
        // Apply multiple passes to handle all transformations
        boolean changed;
        do {
            changed = false;
            
            // Pass 1: Replace new ScriptResult(expr) with expr (must run BEFORE type replacement)
            ConstructorTransformer constructorTransformer = new ConstructorTransformer();
            cu.accept(constructorTransformer, null);
            if (constructorTransformer.wasModified()) {
                changed = true;
                modified = true;
            }
            
            // Pass 2: Replace ScriptResult type references with Object
            TypeReplacementTransformer typeTransformer = new TypeReplacementTransformer();
            cu.accept(typeTransformer, null);
            if (typeTransformer.wasModified()) {
                changed = true;
                modified = true;
            }
            
            // Pass 3: Remove .getJavaScriptResult() calls
            MethodCallTransformer methodTransformer = new MethodCallTransformer();
            cu.accept(methodTransformer, null);
            if (methodTransformer.wasModified()) {
                changed = true;
                modified = true;
            }
            
        } while (changed); // Repeat until no more changes
        
        if (modified) {
            // Write back the modified file
            PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            config.setPrintComments(true);
            config.setEndOfLineCharacter("\n");
            PrettyPrinter printer = new PrettyPrinter(config);
            String modifiedContent = printer.print(cu);
            
            // Write to file
            try {
                java.io.FileWriter writer = new java.io.FileWriter(javaFile);
                writer.write(modifiedContent);
                writer.close();
                System.out.println("Modified: " + javaFile.getPath());
            } catch (java.io.IOException e) {
                System.err.println("Failed to write file: " + javaFile.getPath() + " - " + e.getMessage());
            }
        }
    }
    
    private static class TypeReplacementTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Replace ScriptResult type references with Object
            if (n.getNameAsString().equals("ScriptResult")) {
                modified = true;
                return new ClassOrInterfaceType(null, "Object");
            }
            return super.visit(n, arg);
        }
    }
    
    private static class ConstructorTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new ScriptResult(...) expression
            if (n.getType().asString().equals("ScriptResult")) {
                // Get the argument (should be the result of executeScript)
                if (n.getArguments().size() == 1) {
                    Expression argument = n.getArguments().get(0);
                    modified = true;
                    return argument.clone();
                }
            }
            
            return super.visit(n, arg);
        }
    }
    
    private static class MethodCallTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for .getJavaScriptResult() calls
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                Optional<Expression> scopeOpt = n.getScope();
                if (scopeOpt.isPresent()) {
                    modified = true;
                    
                    // Return just the scope (remove the method call)
                    return scopeOpt.get();
                }
            }
            
            return super.visit(n, arg);
        }
    }
}