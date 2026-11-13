package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Transformation rule for SnakeYAML 2.1 breaking change:
 * - Removed: org.yaml.snakeyaml.inspector.TrustedTagInspector class
 * - Replacement: Implement TagInspector interface with lambda: tag -> true
 * 
 * This transformation:
 * 1. Removes imports of TrustedTagInspector
 * 2. Replaces new TrustedTagInspector() with lambda expression (tag) -> true
 * 3. Handles the case where TrustedTagInspector allowed all tags (always returns true)
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("Applies transformation for SnakeYAML 2.1 TrustedTagInspector → TagInspector lambda");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: TrustedTagInspector → TagInspector lambda (tag -> true)");
        
        try {
            transformSnakeYAMLTrustedTagInspector(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformSnakeYAMLTrustedTagInspector(String sourceDir) throws IOException {
        Path start = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(start)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            
            if (cu == null) {
                return;
            }
            
            boolean modified = false;
            
            // Remove import of TrustedTagInspector (org.yaml.snakeyaml.inspector.TrustedTagInspector)
            List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                .filter(imp -> imp.getNameAsString().equals("org.yaml.snakeyaml.inspector.TrustedTagInspector"))
                .collect(Collectors.toList());
            
            if (!importsToRemove.isEmpty()) {
                cu.getImports().removeAll(importsToRemove);
                modified = true;
            }
            
            // Transform all occurrences of new TrustedTagInspector()
            TrustedTagInspectorTransformer transformer = new TrustedTagInspectorTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                modified = true;
            }
            
            if (modified) {
                String transformedCode = cu.toString();
                Files.write(javaFile, transformedCode.getBytes());
                System.out.println("Transformed: " + javaFile);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
    
    private static class TrustedTagInspectorTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is new TrustedTagInspector()
            if (n.getType().asString().equals("TrustedTagInspector")) {
                modified = true;
                
                // Create lambda expression: (tag) -> true
                // TrustedTagInspector allowed all tags, so lambda always returns true
                // TagInspector is a functional interface with method: boolean isGlobalTagAllowed(Tag tag)
                LambdaExpr lambdaExpr = new LambdaExpr();
                lambdaExpr.setEnclosingParameters(true);
                lambdaExpr.addParameter(new com.github.javaparser.ast.body.Parameter(new UnknownType(), "tag"));
                lambdaExpr.setBody(new ExpressionStmt(new BooleanLiteralExpr(true)));
                return lambdaExpr;
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check for qualified references (unlikely but possible)
            if (n.getNameAsString().equals("TrustedTagInspector")) {
                System.out.println("Warning: Found field access to TrustedTagInspector. May need manual inspection: " + n);
            }
            return super.visit(n, arg);
        }
    }
}