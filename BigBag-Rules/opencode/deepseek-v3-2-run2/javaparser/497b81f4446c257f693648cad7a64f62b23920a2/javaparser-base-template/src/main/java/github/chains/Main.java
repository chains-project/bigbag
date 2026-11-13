package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule to fix breaking dependency changes where cactoos library
 * was removed from the http dependency v1.1.2-d.
 * 
 * This transformation replaces:
 *   new HexOf(new BytesOf(byteArray)).asString()
 * with:
 *   java.util.HexFormat.of().formatHex(byteArray)
 * 
 * The rule is parameterized by fully-qualified type names and method signatures
 * from the dependency, making it applicable to any project affected by this breaking change.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
        
        System.out.println("Transformation completed. Processed " + javaFiles.size() + " files.");
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        System.out.println("Processing: " + javaFile);
        
        String originalContent = cu.toString();
        
        HexOfBytesOfTransformer transformer = new HexOfBytesOfTransformer();
        cu.accept(transformer, null);
        
        // Remove cactoos imports if they exist
        cu.getImports().removeIf(importDecl -> 
            importDecl.getNameAsString().startsWith("org.cactoos.io") ||
            importDecl.getNameAsString().startsWith("org.cactoos.text")
        );
        
        // Add HexFormat import if needed
        HexFormatImportChecker importChecker = new HexFormatImportChecker();
        cu.accept(importChecker, null);
        if (importChecker.usesHexFormat()) {
            cu.addImport("java.util.HexFormat");
        }
        
        String transformedContent = cu.toString();
        
        if (!originalContent.equals(transformedContent)) {
            System.out.println("  File was transformed!");
            Files.write(javaFile, transformedContent.getBytes());
        } else {
            System.out.println("  No changes made");
        }
    }
    
    /**
     * Visitor that transforms HexOf(BytesOf(...)).asString() patterns
     * to HexFormat.of().formatHex(...)
     */
    private static class HexOfBytesOfTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // Check if this is a .asString() call
            if (n.getNameAsString().equals("asString")) {
                System.out.println("  Found asString call at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                if (n.getScope().isPresent()) {
                    Node scope = n.getScope().get();
                    System.out.println("    Scope type: " + scope.getClass().getSimpleName());
                    
                    // Look for HexOf pattern in ObjectCreationExpr
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objCreation = (ObjectCreationExpr) scope;
                        String objType = objCreation.getType().getNameAsString();
                        System.out.println("    ObjectCreation type: " + objType);
                        
                        // Check if this is HexOf or if it contains HexOf in arguments
                        if (objType.equals("HexOf") || objType.endsWith(".HexOf")) {
                            // Direct HexOf pattern
                            if (objCreation.getArguments().size() == 1) {
                                Node hexOfArg = objCreation.getArguments().get(0);
                                if (hexOfArg instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr bytesOfCreation = (ObjectCreationExpr) hexOfArg;
                                    String bytesOfType = bytesOfCreation.getType().getNameAsString();
                                    if (bytesOfType.equals("BytesOf") || bytesOfType.endsWith(".BytesOf")) {
                                        System.out.println("    *** FOUND DIRECT PATTERN! Replacing...");
                                        return createHexFormatCall(bytesOfCreation.getArguments());
                                    }
                                }
                            }
                        } else {
                            // Check if any argument contains HexOf pattern
                            for (Node argument : objCreation.getArguments()) {
                                if (argument.toString().contains("new HexOf(new BytesOf")) {
                                    System.out.println("    *** FOUND NESTED PATTERN! Replacing...");
                                    // Extract the BytesOf argument
                                    String argStr = argument.toString();
                                    int bytesOfStart = argStr.indexOf("new BytesOf(") + "new BytesOf(".length();
                                    int bytesOfEnd = argStr.lastIndexOf(")");
                                    if (bytesOfStart > 0 && bytesOfEnd > bytesOfStart) {
                                        String bytesOfArg = argStr.substring(bytesOfStart, bytesOfEnd);
                                        JavaParser parser = new JavaParser();
                                        com.github.javaparser.ast.expr.Expression expr = parser.parseExpression(bytesOfArg).getResult().orElseThrow();
                                        return createHexFormatCall(new com.github.javaparser.ast.NodeList<>(expr));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return n;
        }
        
        private MethodCallExpr createHexFormatCall(com.github.javaparser.ast.NodeList<com.github.javaparser.ast.expr.Expression> arguments) {
            return new MethodCallExpr(
                new MethodCallExpr(
                    new com.github.javaparser.ast.expr.NameExpr("HexFormat"),
                    "of"
                ),
                "formatHex",
                arguments
            );
        }
    }
    
    /**
     * Visitor to check if HexFormat is used in the compilation unit
     */
    private static class HexFormatImportChecker extends ModifierVisitor<Void> {
        private boolean usesHexFormat = false;
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            if (n.getNameAsString().equals("formatHex")) {
                if (n.getScope().isPresent()) {
                    Node scope = n.getScope().get();
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) scope;
                        if (methodCall.getNameAsString().equals("of")) {
                            if (methodCall.getScope().isPresent()) {
                                Node ofScope = methodCall.getScope().get();
                                if (ofScope.toString().equals("HexFormat")) {
                                    usesHexFormat = true;
                                }
                            }
                        }
                    }
                }
            }
            return n;
        }
        
        public boolean usesHexFormat() {
            return usesHexFormat;
        }
    }
}