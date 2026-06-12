package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);

        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            System.out.println("Found " + javaFiles.size() + " Java files");

            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }

            System.out.println("Modified " + modifiedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        if (cu == null) {
            return false;
        }

        boolean modified = false;

        // Remove imports from org.bouncycastle.crypto.tls
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("org.bouncycastle.crypto.tls.")) {
                importsToRemove.add(importDecl);
                modified = true;
            }
        }
        importsToRemove.forEach(Node::remove);

        // Replace TlsFatalAlert constructor calls
        List<ObjectCreationExpr> tlsAlerts = cu.findAll(ObjectCreationExpr.class, 
            expr -> {
                try {
                    return expr.getType().asString().equals("TlsFatalAlert");
                } catch (Exception e) {
                    return false;
                }
            });

        for (ObjectCreationExpr alert : tlsAlerts) {
            // Get the parent throw statement
            Node parent = alert.getParentNode().orElse(null);
            if (parent instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) parent;
                
                // Check if constructor has an AlertDescription argument
                if (alert.getArguments().size() == 1) {
                    Expression arg = alert.getArguments().get(0);
                    String alertDesc = extractAlertDescription(arg);
                    
                    // Replace with InvalidCipherTextException
                    ObjectCreationExpr newException = new ObjectCreationExpr();
                    newException.setType("org.bouncycastle.crypto.InvalidCipherTextException");
                    newException.addArgument(new StringLiteralExpr("TLS alert: " + alertDesc));
                    
                    throwStmt.setExpression(newException);
                    modified = true;
                    
                    // Add import if not already present
                    boolean hasImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("org.bouncycastle.crypto.InvalidCipherTextException"));
                    if (!hasImport) {
                        cu.addImport("org.bouncycastle.crypto.InvalidCipherTextException");
                    }
                }
            }
        }

        // Also handle any direct references to AlertDescription constants
        // by replacing them with string literals
        List<FieldAccessExpr> alertDescRefs = cu.findAll(FieldAccessExpr.class,
            expr -> {
                try {
                    return expr.getScope().isNameExpr() && 
                           expr.getScope().asNameExpr().getNameAsString().equals("AlertDescription");
                } catch (Exception e) {
                    return false;
                }
            });

        for (FieldAccessExpr fieldAccess : alertDescRefs) {
            // Replace AlertDescription.X with "X"
            String fieldName = fieldAccess.getNameAsString();
            StringLiteralExpr replacement = new StringLiteralExpr(fieldName);
            fieldAccess.replace(replacement);
            modified = true;
        }

        if (modified) {
            // Write back the modified file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String newContent = printer.print(cu);
            
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Modified: " + javaFile);
            return true;
        }

        return false;
    }

    private static String extractAlertDescription(Expression arg) {
        if (arg instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) arg;
            if (fieldAccess.getScope().isNameExpr() && 
                fieldAccess.getScope().asNameExpr().getNameAsString().equals("AlertDescription")) {
                return fieldAccess.getNameAsString();
            }
        }
        
        // Fallback: try to get string representation
        return arg.toString().replace("\"", "");
    }
}