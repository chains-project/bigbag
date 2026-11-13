package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation to fix breaking API changes in dependencies.
 * 
 * This transformation handles common patterns where constructor signatures
 * have changed in library updates, specifically for hamcrest matchers.
 * 
 * Current transformations:
 * 1. StringContains(boolean, String) → StringContains(String)
 * 2. StringStartsWith(boolean, String) → StringStartsWith(String)
 * 
 * The transformation is applied to Java files that import from org.hamcrest
 * packages, ensuring it only affects code using the affected library.
 * 
 * Usage: java -jar javaparser.jar <source-directory>
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Applies automatic fixes for breaking API changes in dependencies.");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        JavaParser parser = new JavaParser();
        PrettyPrinter printer = new PrettyPrinter(new PrettyPrinterConfiguration()
            .setOrderImports(true)
            .setPrintComments(true));
            
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu != null) {
                // Check imports to see if this file uses hamcrest
                // This ensures we only transform files actually using the affected library
                boolean usesHamcrest = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().startsWith("org.hamcrest."));
                
                if (usesHamcrest) {
                    ApiFixVisitor visitor = new ApiFixVisitor();
                    cu.accept(visitor, null);
                    String transformed = printer.print(cu);
                    Files.write(javaFile, transformed.getBytes());
                }
            }
        }
    }
    
    /**
     * Visitor that fixes breaking API changes in constructor calls.
     * 
     * The transformation identifies specific constructor patterns that are
     * known to have changed in library updates and applies the necessary
     * modifications to make the code compatible with the new API.
     */
    static class ApiFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            ClassOrInterfaceType type = expr.getType();
            String typeName = type.getNameAsString();
            
            // Pattern: Constructors that previously took an extra boolean parameter
            // for case-sensitivity control, now take only the string pattern.
            // Example: StringContains(boolean, String) → StringContains(String)
            if (("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) 
                && expr.getArguments().size() == 2) {
                // Remove the first boolean argument (case-sensitivity flag)
                expr.getArguments().remove(0);
            }
            
            return super.visit(expr, arg);
        }
    }
}