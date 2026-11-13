package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these can be changed for different breaking changes
    private static final String OLD_PACKAGE_PREFIX = "org.bouncycastle.crypto.tls";
    private static final String OLD_EXCEPTION_CLASS = "TlsFatalAlert";
    private static final String OLD_ENUM_CLASS = "AlertDescription";
    private static final String NEW_EXCEPTION_CLASS = "IOException";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Remove old imports
        List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                .filter(importDecl -> importDecl.getNameAsString().startsWith(OLD_PACKAGE_PREFIX))
                .collect(Collectors.toList());
        
        if (!importsToRemove.isEmpty()) {
            cu.getImports().removeAll(importsToRemove);
            modified = true;
        }
        
        // Add new import if needed
        boolean hasIOExceptionImport = cu.getImports().stream()
                .anyMatch(importDecl -> importDecl.getNameAsString().equals("java.io.IOException"));
        
        if (!hasIOExceptionImport && needsIOExceptionImport(cu)) {
            cu.addImport("java.io.IOException");
            modified = true;
        }
        
        // Transform object creation expressions
        TlsExceptionTransformer transformer = new TlsExceptionTransformer();
        cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            modified = true;
        }
        
        if (modified) {
            Files.writeString(filePath, cu.toString());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    private static boolean needsIOExceptionImport(CompilationUnit cu) {
        String code = cu.toString();
        return code.contains("new " + NEW_EXCEPTION_CLASS + "(") || 
               code.contains("throws " + NEW_EXCEPTION_CLASS);
    }
    
    private static class TlsExceptionTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is creating a TlsFatalAlert
            if (n.getType().asString().equals(OLD_EXCEPTION_CLASS)) {
                // Replace with new IOException
                ObjectCreationExpr newExpr = new ObjectCreationExpr(
                    null, 
                    new com.github.javaparser.ast.type.ClassOrInterfaceType(null, NEW_EXCEPTION_CLASS), 
                    n.getArguments()
                );
                modified = true;
                return newExpr;
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing AlertDescription enum values
            if (n.getScope().isNameExpr() && 
                n.getScope().asNameExpr().getNameAsString().equals(OLD_ENUM_CLASS)) {
                // Replace with empty string (remove the enum value argument)
                // In the context of IOException constructor, we don't need the alert description
                modified = true;
                return new com.github.javaparser.ast.expr.StringLiteralExpr("");
            }
            return super.visit(n, arg);
        }
    }
}