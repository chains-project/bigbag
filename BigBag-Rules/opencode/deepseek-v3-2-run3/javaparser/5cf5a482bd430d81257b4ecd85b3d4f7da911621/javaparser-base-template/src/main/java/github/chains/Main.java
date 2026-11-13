package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms javax.mvc to jakarta.mvc and related javax.* to jakarta.* imports");
            System.err.println("for compatibility with Jakarta MVC API 2.0+");
            System.exit(1);
        }
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        try {
            processDirectory(sourcePath);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processDirectory(Path directory) throws IOException {
        List<Path> javaFiles = Files.walk(directory).filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            processJavaFile(javaFile);
        }
    }

    private static void processJavaFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(() -> new IOException("Failed to parse " + javaFile));
        // Create visitor to transform javax to jakarta for MVC API 2.0+ compatibility
        JakartaMvcMigrationVisitor visitor = new JakartaMvcMigrationVisitor();
        cu.accept(visitor, null);
        // Write the modified file
        Files.write(javaFile, cu.toString().getBytes());
    }

    /**
     * Visitor that transforms Java EE (javax) imports and type references
     * to Jakarta EE (jakarta) for compatibility with Jakarta MVC API 2.0+.
     *
     * This handles the breaking change where javax.mvc.* packages were
     * moved to jakarta.mvc.* namespace, and any related javax.* packages
     * referenced by the MVC API (like javax.ws.rs) also need to be updated
     * to jakarta.* namespace when using Jakarta MVC 2.0+.
     */
    private static class JakartaMvcMigrationVisitor extends ModifierVisitor<Void> {

        /**
         * Transforms import declarations from javax.* to jakarta.*
         * for packages referenced by Jakarta MVC API 2.0+
         */
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String importName = n.getNameAsString();
            String transformed = transformPackage(importName);
            if (!transformed.equals(importName)) {
                n.setName(new Name(transformed));
            }
            return (Node) super.visit(n, arg);
        }

        /**
         * Transforms type references in code from javax.* to jakarta.*
         */
        @Override
        public Node visit(NameExpr n, Void arg) {
            String name = n.getNameAsString();
            String transformed = transformTypeReference(name);
            if (!transformed.equals(name)) {
                n.setName(transformed);
            }
            return (Node) super.visit(n, arg);
        }

        // Note: We don't override visit(Name) because Name nodes are part of
        // NameExpr nodes. We transform the full qualified name in visit(NameExpr).
        // If we transformed individual Name nodes, we'd get double transformations
        // for qualified names like "javax.ws.rs.core.Context".

        /**
         * Transforms package names from javax.* to jakarta.* for packages
         * referenced by Jakarta MVC API 2.0+
         */
        private String transformPackage(String packageName) {
            // Transform javax.mvc to jakarta.mvc (core breaking change)
            if (packageName.startsWith("javax.mvc")) {
                return packageName.replaceFirst("^javax\\.mvc", "jakarta.mvc");
            }
            // Transform javax.ws.rs to jakarta.ws.rs (MVC API 2.0+ depends on Jakarta WS-RS)
            if (packageName.startsWith("javax.ws.rs")) {
                return packageName.replaceFirst("^javax\\.ws\\.rs", "jakarta.ws.rs");
            }
            // Add other javax.* to jakarta.* transformations as needed
            // for other packages referenced by Jakarta MVC API
            return packageName;
        }

        /**
         * Transforms type references from javax.* to jakarta.*
         */
        private String transformTypeReference(String typeName) {
            // Transform javax.mvc.* to jakarta.mvc.*
            if (typeName.startsWith("javax.mvc.")) {
                return typeName.replaceFirst("^javax\\.mvc\\.", "jakarta.mvc.");
            } else if (typeName.equals("javax.mvc")) {
                return "jakarta.mvc";
            }
            // Transform javax.ws.rs.* to jakarta.ws.rs.*
            if (typeName.startsWith("javax.ws.rs.")) {
                return typeName.replaceFirst("^javax\\.ws\\.rs\\.", "jakarta.ws.rs.");
            } else if (typeName.equals("javax.ws.rs")) {
                return "jakarta.ws.rs";
            }
            return typeName;
        }
    }
}
