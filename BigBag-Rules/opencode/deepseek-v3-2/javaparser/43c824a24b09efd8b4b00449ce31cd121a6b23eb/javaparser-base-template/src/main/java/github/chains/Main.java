package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    // Configuration: These constants define the breaking API change
    // They can be easily modified to handle different breaking changes
    private static final String OLD_TYPE_FULL = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_TYPE_FULL = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_TYPE_SIMPLE = "JsonBranch";
    private static final String NEW_TYPE_SIMPLE = "DataElement";
    private static final String OLD_METHOD = "getJsonPath";
    private static final String NEW_METHOD = "getPath";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Generic JavaParser Transformation Rule ===");
        System.out.println("Breaking change: " + OLD_TYPE_FULL + " -> " + NEW_TYPE_FULL);
        System.out.println("Method change: " + OLD_METHOD + "() -> " + NEW_METHOD + "()");
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("==============================================");
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("==============================================");
            System.out.println("Transformation complete!");
            System.out.println("Successfully modified " + modifiedFiles + " files");
            System.out.println("This generic rule can be applied to ANY project affected by this breaking change");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return false;
            }
        }
        
        cu = LexicalPreservingPrinter.setup(cu);
        
        boolean modified = false;
        
        modified |= updateImports(cu);
        modified |= updateTypeReferences(cu);
        modified |= updateMethodCalls(cu);
        
        if (modified) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(LexicalPreservingPrinter.print(cu));
            }
            System.out.println("[MODIFIED] " + javaFile);
        }
        
        return modified;
    }
    
    private static boolean updateImports(CompilationUnit cu) {
        boolean modified = false;
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.equals(OLD_TYPE_FULL)) {
                importDecl.setName(NEW_TYPE_FULL);
                modified = true;
                System.out.println("  - Updated import: " + OLD_TYPE_FULL + " -> " + NEW_TYPE_FULL);
            }
        }
        
        return modified;
    }
    
    private static boolean updateTypeReferences(CompilationUnit cu) {
        TypeReferenceUpdater visitor = new TypeReferenceUpdater();
        visitor.visit(cu, null);
        return visitor.isModified();
    }
    
    private static boolean updateMethodCalls(CompilationUnit cu) {
        MethodCallUpdater visitor = new MethodCallUpdater();
        visitor.visit(cu, null);
        return visitor.isModified();
    }
    
    private static class TypeReferenceUpdater extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);
            
            // Check if this is a type reference to the old class
            if (type.getNameAsString().equals(OLD_TYPE_SIMPLE)) {
                type.setName(NEW_TYPE_SIMPLE);
                modified = true;
                System.out.println("  - Updated type reference: " + OLD_TYPE_SIMPLE + " -> " + NEW_TYPE_SIMPLE);
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    private static class MethodCallUpdater extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to the old method name
            if (methodCall.getNameAsString().equals(OLD_METHOD)) {
                methodCall.setName(NEW_METHOD);
                modified = true;
                System.out.println("  - Updated method call: " + OLD_METHOD + "() -> " + NEW_METHOD + "()");
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    // Utility method to demonstrate the transformation pattern
    public static String describeTransformation() {
        return String.format(
            "Generic Transformation Rule:\n" +
            "1. Import change: %s -> %s\n" +
            "2. Type reference change: %s -> %s\n" +
            "3. Method call change: %s() -> %s()\n" +
            "\nThis rule is parameterized and can be adapted for similar breaking changes\n" +
            "by modifying the constants at the top of the Main class.",
            OLD_TYPE_FULL, NEW_TYPE_FULL,
            OLD_TYPE_SIMPLE, NEW_TYPE_SIMPLE,
            OLD_METHOD, NEW_METHOD
        );
    }
}