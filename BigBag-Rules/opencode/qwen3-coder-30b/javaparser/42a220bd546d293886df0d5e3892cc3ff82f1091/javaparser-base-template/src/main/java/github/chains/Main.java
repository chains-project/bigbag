package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        File directory = new File(sourceDir);
        
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        // Visitor to find and transform DigestUtils.md5Hex calls
        VoidVisitorAdapter<Void> visitor = new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(CompilationUnit cu, Void arg) {
                super.visit(cu, arg);
                
                // Add necessary imports if they don't exist
                boolean hasBase64Import = false;
                boolean hasMessageDigestImport = false;
                
                for (ImportDeclaration importDecl : cu.getImports()) {
                    if (importDecl.getNameAsString().equals("java.util.Base64")) {
                        hasBase64Import = true;
                    } else if (importDecl.getNameAsString().equals("java.security.MessageDigest")) {
                        hasMessageDigestImport = true;
                    }
                }
                
                if (!hasBase64Import) {
                    cu.addImport("java.util.Base64");
                }
                if (!hasMessageDigestImport) {
                    cu.addImport("java.security.MessageDigest");
                }
                
                // Remove DigestUtils import if it exists
                for (int i = cu.getImports().size() - 1; i >= 0; i--) {
                    ImportDeclaration importDecl = cu.getImports().get(i);
                    if (importDecl.getNameAsString().equals("org.apache.commons.codec.digest.DigestUtils")) {
                        cu.getImports().remove(i);
                    }
                }
            }
            
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a call to DigestUtils.md5Hex
                if (n.getName().asString().equals("md5Hex") && 
                    n.getScope().isPresent() && 
                    n.getScope().get() instanceof NameExpr) {
                    
                    NameExpr scope = (NameExpr) n.getScope().get();
                    if (scope.getName().asString().equals("DigestUtils")) {
                        // Replace DigestUtils.md5Hex(x) with base64 encoded MD5 hash
                        // using standard Java MessageDigest
                        
                        // Get the argument to md5Hex
                        if (!n.getArguments().isEmpty()) {
                            // Replace the entire method call with a new expression
                            String argument = n.getArguments().get(0).toString();
                            
                            // Create the new expression: Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(x.getBytes()))
                            String newExpression = "Base64.getEncoder().encodeToString(MessageDigest.getInstance(\"MD5\").digest(" + argument + ".getBytes()))";
                            
                            // Replace the method call with the new expression
                            n.replace(StaticJavaParser.parseExpression(newExpression));
                        }
                    }
                }
            }
        };
        
        // Process all Java files in the source directory
        Files.walkFileTree(Paths.get(sourceDir), new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    try {
                        // Read the file
                        String sourceCode = Files.readString(file);
                        
                        // Parse the compilation unit
                        CompilationUnit cu = StaticJavaParser.parse(sourceCode);
                        
                        // Apply the visitor
                        visitor.visit(cu, null);
                        
                        // Write back the modified code
                        Files.write(file, cu.toString().getBytes());
                        
                        System.out.println("Transformed: " + file);
                    } catch (Exception e) {
                        System.err.println("Error processing " + file + ": " + e.getMessage());
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}