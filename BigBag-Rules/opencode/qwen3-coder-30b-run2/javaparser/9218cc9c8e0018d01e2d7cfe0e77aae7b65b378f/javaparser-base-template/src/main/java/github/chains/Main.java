package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifyingVisitor;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            // Process all Java files in the source directory
            String sourceDir = args.length > 0 ? args[0] : "/workspace/wicket-crudifier/src";
            
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        System.out.println("Processing: " + path);
                        String code = Files.readString(path);
                        CompilationUnit cu = StaticJavaParser.parse(code);
                        
                        // Apply the transformation
                        TransformationVisitor visitor = new TransformationVisitor();
                        cu.accept(visitor, null);
                        
                        // Save the modified code back to file
                        Files.writeString(path, cu.toString());
                    } catch (Exception e) {
                        System.err.println("Error processing file " + path + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static class TransformationVisitor extends ModifyingVisitor {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Transform javax.validation imports to jakarta.validation
            if (importDecl.getNameAsString().startsWith("javax.validation")) {
                String newImport = importDecl.getNameAsString().replaceFirst("javax.validation", "jakarta.validation");
                importDecl.setName(newImport);
            }
            return super.visit(importDecl, arg);
        }
    }
}
