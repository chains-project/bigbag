package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.*;
import java.nio.file.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java Main <sourceDirectory> <oldFullyQualifiedType> <newFullyQualifiedType>");
            System.err.println("Example: java Main /path/to/src com.gargoylesoftware.htmlunit.ScriptResult java.lang.Object");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String oldFullyQualifiedType = args[1];
        String newFullyQualifiedType = args[2];
        
        System.out.println("Transforming source directory: " + sourceDirectory);
        System.out.println("Transforming type: " + oldFullyQualifiedType + " -> " + newFullyQualifiedType);
        
        Path sourcePath = Paths.get(sourceDirectory);
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        try (Stream<Path> walk = Files.walk(sourcePath)) {
            walk.filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFile -> transformFile(javaFile, oldFullyQualifiedType, newFullyQualifiedType));
        }
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path javaFile, String oldTypeName, String newTypeName) {
        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
            
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse: " + javaFile);
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                return;
            }
            
            LexicalPreservingPrinter.setup(cu);
            
            HtmlUnitScriptResultTransformer transformer = new HtmlUnitScriptResultTransformer(oldTypeName, newTypeName);
            cu.accept(transformer, null);
            
            if (transformer.transformationsApplied > 0) {
                try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                    writer.write(LexicalPreservingPrinter.print(cu));
                }
                System.out.println("Transformed " + transformer.transformationsApplied + " occurrences in " + javaFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
        }
    }
    
    static class HtmlUnitScriptResultTransformer extends ModifierVisitor<Void> {
        private final String oldTypeName;
        private final String newTypeName;
        private final String simpleOldTypeName;
        private final String simpleNewTypeName;
        int transformationsApplied = 0;
        
        public HtmlUnitScriptResultTransformer(String oldFullyQualifiedType, String newFullyQualifiedType) {
            this.oldTypeName = oldFullyQualifiedType;
            this.newTypeName = newFullyQualifiedType;
            this.simpleOldTypeName = oldFullyQualifiedType.substring(oldFullyQualifiedType.lastIndexOf('.') + 1);
            this.simpleNewTypeName = newFullyQualifiedType.substring(newFullyQualifiedType.lastIndexOf('.') + 1);
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            if (importDecl.getNameAsString().equals(oldTypeName)) {
                transformationsApplied++;
                System.out.println("  Removing import: " + oldTypeName);
                return null;
            }
            return importDecl;
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);
            
            if (type.getNameAsString().equals(simpleOldTypeName)) {
                transformationsApplied++;
                System.out.println("  Changing type: " + simpleOldTypeName + " -> " + simpleNewTypeName);
                type.setName(simpleNewTypeName);
            }
            
            return type;
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Visit children first
            super.visit(expr, arg);
            
            // Check if this is new ScriptResult(something) that should become just something
            String typeName = expr.getType().getNameAsString();
            if ((typeName.equals(simpleOldTypeName) || typeName.equals(simpleNewTypeName)) && expr.getArguments().size() == 1) {
                transformationsApplied++;
                System.out.println("  Transforming: new " + typeName + "(arg) -> arg");
                return expr.getArgument(0);
            }
            
            return expr;
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Handle scriptResult.getJavaScriptResult() -> scriptResult
            if (expr.getNameAsString().equals("getJavaScriptResult")) {
                Expression scope = expr.getScope().orElse(null);
                if (scope != null) {
                    transformationsApplied++;
                    System.out.println("  Transforming: " + expr + " -> " + scope);
                    return scope;
                }
            }
            
            return expr;
        }
    }
}