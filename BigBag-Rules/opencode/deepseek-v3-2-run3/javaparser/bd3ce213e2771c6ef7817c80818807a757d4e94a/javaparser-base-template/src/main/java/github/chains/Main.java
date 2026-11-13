package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            // Walk through all Java files
            Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(javaFile -> {
                    try {
                        processJavaFile(javaFile);
                    } catch (Exception e) {
                        System.err.println("Error processing file: " + javaFile);
                        e.printStackTrace();
                    }
                });
            
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        System.out.println("Processing: " + javaFile);
        
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + javaFile)
        );
        
        // Track if we modified the file
        final boolean[] modified = {false};
        
        // Find all method calls to writeValue or readValue (both affected)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(mc -> mc.getNameAsString().equals("writeValue") || mc.getNameAsString().equals("readValue"))
            .forEach(methodCall -> {
                // Check if already in try-catch
                if (!isInTryCatch(methodCall)) {
                    wrapWithTryCatch(methodCall);
                    modified[0] = true;
                }
            });
        
        if (modified[0]) {
            // Write back the modified file
            Files.write(javaFile, cu.toString().getBytes());
            System.out.println("  Modified: " + javaFile);
        }
    }
    
    private static boolean isInTryCatch(MethodCallExpr methodCall) {
        // Check if already in a try statement
        return methodCall.findAncestor(TryStmt.class).isPresent();
    }
    
    private static void wrapWithTryCatch(MethodCallExpr methodCall) {
        // Create try block with the method call
        BlockStmt tryBlock = new BlockStmt();
        tryBlock.addStatement(methodCall.clone());
        
        // Create catch clause for Exception
        Parameter param = new Parameter();
        param.setType(new ClassOrInterfaceType(null, "Exception"));
        param.setName("e");
        
        BlockStmt catchBody = new BlockStmt();
        // Create throw statement using parse
        JavaParser parser = new JavaParser();
        var parseResult = parser.parseStatement("throw new java.io.IOException(\"Error writing JSON value\", e);");
        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            catchBody.addStatement(parseResult.getResult().get());
        } else {
            // Fallback: create simple throw
            catchBody.addStatement(new com.github.javaparser.ast.stmt.ThrowStmt(
                new com.github.javaparser.ast.expr.NameExpr("e")
            ));
        }
        
        CatchClause catchClause = new CatchClause(param, catchBody);
        
        // Create try statement
        TryStmt tryStmt = new TryStmt();
        tryStmt.setTryBlock(tryBlock);
        tryStmt.setCatchClauses(new NodeList<>(catchClause));
        
        // Replace the expression statement containing the method call
        Optional<com.github.javaparser.ast.stmt.Statement> parentStmt = methodCall.findAncestor(com.github.javaparser.ast.stmt.Statement.class);
        if (parentStmt.isPresent()) {
            parentStmt.get().replace(tryStmt);
        } else {
            // Fallback: try to replace the method call itself
            methodCall.replace(tryStmt);
        }
    }
}