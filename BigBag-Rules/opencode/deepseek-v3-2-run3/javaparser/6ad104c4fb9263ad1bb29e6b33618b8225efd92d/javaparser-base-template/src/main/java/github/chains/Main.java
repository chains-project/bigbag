package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    // Configuration for the breaking change transformation
    private static final Map<String, Replacement> CLASS_REPLACEMENTS = new HashMap<>();
    
    static {
        // Define transformations for removed TLS classes
        CLASS_REPLACEMENTS.put("TlsFatalAlert", new Replacement("IOException", true));
        // Add more class replacements as needed for other removed classes
    }
    
    static class Replacement {
        final String newType;
        final boolean replaceConstructor; // Whether to replace constructor calls
        
        Replacement(String newType, boolean replaceConstructor) {
            this.newType = newType;
            this.replaceConstructor = replaceConstructor;
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms code broken by Bouncy Castle 1.67 TLS package removal");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing directory: " + sourceDir);
        System.out.println("Transforming broken TLS code from org.bouncycastle.crypto.tls package");
        
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        for (File javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
        System.out.println("\nSummary of transformations:");
        System.out.println("1. Removed imports from org.bouncycastle.crypto.tls.*");
        System.out.println("2. Replaced TlsFatalAlert constructor calls with IOException");
        System.out.println("3. Converted AlertDescription constants to descriptive error messages");
    }
    
    private static boolean transformFile(File file) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream(file)).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Remove imports from org.bouncycastle.crypto.tls package
        List<ImportDeclaration> importsToRemove = cu.getImports().stream()
            .filter(imp -> imp.getNameAsString().startsWith("org.bouncycastle.crypto.tls"))
            .collect(Collectors.toList());
        
        if (!importsToRemove.isEmpty()) {
            cu.getImports().removeAll(importsToRemove);
            modified = true;
            System.out.println("  Removed imports from " + file.getPath() + ": " +
                importsToRemove.stream().map(ImportDeclaration::getNameAsString).collect(Collectors.joining(", ")));
        }
        
        // Transform removed TLS class usage
        TlsTransformationVisitor visitor = new TlsTransformationVisitor();
        cu.accept(visitor, null);
        if (visitor.modified) {
            modified = true;
        }
        
        if (modified) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(cu.toString().getBytes());
            }
            return true;
        }
        
        return false;
    }
    
    static class TlsTransformationVisitor extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().toString();
            Replacement replacement = CLASS_REPLACEMENTS.get(typeName);
            
            if (replacement != null && replacement.replaceConstructor) {
                // Replace constructor call with new type
                ObjectCreationExpr newExpr = new ObjectCreationExpr();
                newExpr.setType(replacement.newType);
                
                // Handle special cases
                if ("TlsFatalAlert".equals(typeName)) {
                    transformTlsFatalAlert(n, newExpr);
                } else {
                    // Generic replacement - keep original arguments
                    n.getArguments().forEach(newExpr::addArgument);
                }
                
                modified = true;
                return newExpr;
            }
            
            return super.visit(n, arg);
        }
        
        private void transformTlsFatalAlert(ObjectCreationExpr oldExpr, ObjectCreationExpr newExpr) {
            // Check if there's an AlertDescription constant argument
            if (oldExpr.getArguments().size() == 1) {
                com.github.javaparser.ast.expr.Expression argExpr = oldExpr.getArgument(0);
                if (argExpr instanceof FieldAccessExpr) {
                    FieldAccessExpr fieldAccess = (FieldAccessExpr) argExpr;
                    if (fieldAccess.getScope() instanceof NameExpr) {
                        NameExpr nameExpr = (NameExpr) fieldAccess.getScope();
                        if (nameExpr.getNameAsString().equals("AlertDescription")) {
                            // Create descriptive message from AlertDescription constant
                            String alertName = fieldAccess.getNameAsString();
                            String message = "TLS alert: " + alertName;
                            newExpr.addArgument(new StringLiteralExpr(message));
                            return;
                        }
                    }
                }
                // Keep original argument if not AlertDescription.CONSTANT pattern
                newExpr.addArgument(argExpr);
            } else {
                // No arguments or multiple arguments - use generic message
                newExpr.addArgument(new StringLiteralExpr("TLS fatal alert"));
            }
        }
        
        @Override
        public Visitable visit(ThrowStmt n, Void arg) {
            com.github.javaparser.ast.expr.Expression expr = n.getExpression();
            if (expr instanceof ObjectCreationExpr) {
                ObjectCreationExpr objectCreation = (ObjectCreationExpr) expr;
                String typeName = objectCreation.getType().toString();
                if (CLASS_REPLACEMENTS.containsKey(typeName)) {
                    // The visit(ObjectCreationExpr) above will handle the replacement
                    // We just need to mark as modified
                    modified = true;
                }
            }
            return super.visit(n, arg);
        }
        
        // Note: Could add more visitor methods here to handle other patterns like:
        // - Static field accesses (AlertDescription.bad_record_mac)
        // - Method calls on TLS classes
        // - Variable declarations of TLS types
    }
}