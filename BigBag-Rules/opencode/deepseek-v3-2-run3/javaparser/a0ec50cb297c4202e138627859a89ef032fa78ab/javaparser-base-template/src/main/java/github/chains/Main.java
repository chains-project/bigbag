package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        try {
            transformProject(sourceDir);
        } catch (IOException e) {
            System.err.println("Error transforming project: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Remove org.cactoos imports
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration imp : cu.getImports()) {
                String importName = imp.getNameAsString();
                if (importName.startsWith("org.cactoos.")) {
                    importsToRemove.add(imp);
                    modified = true;
                }
            }
            importsToRemove.forEach(imp -> cu.getImports().remove(imp));
            
            // Add Hex import if we found cactoos imports and file doesn't already have it
            if (modified && !hasHexImport(cu)) {
                cu.addImport("org.apache.commons.codec.binary.Hex");
            }
            
            // Add Arrays import if we found ListOf usage and file doesn't already have it
            if (modified && !hasArraysImport(cu)) {
                cu.addImport("java.util.Arrays");
            }
            
            // Transform HexOf/BytesOf patterns
            HexBytesTransformer transformer = new HexBytesTransformer();
            cu.accept(transformer, null);
            modified = modified || transformer.wasModified();
            
            if (modified) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean hasHexImport(CompilationUnit cu) {
        return cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("org.apache.commons.codec.binary.Hex"));
    }
    
    private static boolean hasArraysImport(CompilationUnit cu) {
        return cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.Arrays"));
    }
    
    static class HexBytesTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for pattern: new HexOf(new BytesOf(expr)).asString()
            if (n.getNameAsString().equals("asString")) {
                Optional<ObjectCreationExpr> hexOfCreation = n.getScope()
                    .filter(ObjectCreationExpr.class::isInstance)
                    .map(ObjectCreationExpr.class::cast);
                
                if (hexOfCreation.isPresent()) {
                    ObjectCreationExpr hexOfExpr = hexOfCreation.get();
                    if (hexOfExpr.getTypeAsString().equals("HexOf")) {
                        // Check if HexOf constructor arg is new BytesOf(...)
                        if (!hexOfExpr.getArguments().isEmpty()) {
                            Node argNode = hexOfExpr.getArgument(0);
                            if (argNode instanceof ObjectCreationExpr) {
                                ObjectCreationExpr bytesOfExpr = (ObjectCreationExpr) argNode;
                                if (bytesOfExpr.getTypeAsString().equals("BytesOf")) {
                                    // Found pattern: new HexOf(new BytesOf(expr)).asString()
                                    // Replace with: Hex.encodeHexString(expr)
                                    if (!bytesOfExpr.getArguments().isEmpty()) {
                                        Expression innerExpr = bytesOfExpr.getArgument(0);
                                        MethodCallExpr replacement = new MethodCallExpr(
                                            "Hex.encodeHexString"
                                        );
                                        replacement.addArgument(innerExpr.clone());
                                        modified = true;
                                        return replacement;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check for pattern: new ListOf<T>(...)
            if (n.getTypeAsString().equals("ListOf")) {
                // Replace with: Arrays.asList(...)
                MethodCallExpr replacement = new MethodCallExpr(
                    new NameExpr("Arrays"), "asList"
                );
                for (Expression argExpr : n.getArguments()) {
                    replacement.addArgument(argExpr.clone());
                }
                modified = true;
                return replacement;
            }
            return super.visit(n, arg);
        }
    }
}