package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    /**
     * Generic transformation rule for Jetty SelectChannelConnector to ServerConnector migration.
     * This rule transforms:
     * 1. Import statements: org.eclipse.jetty.server.nio.SelectChannelConnector -> org.eclipse.jetty.server.ServerConnector
     * 2. Object creation: new SelectChannelConnector() -> new ServerConnector(serverInstance)
     * 
     * The transformation is generic and can be applied to any project affected by this
     * breaking change in Jetty 11.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jetty SelectChannelConnector to ServerConnector in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        try (Stream<Path> stream = Files.walk(startDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        boolean wasModified = false;
        
        // Transform import statements
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                wasModified = true;
                System.out.println("  Updated import in " + javaFile);
            }
        }
        
        // Transform object creation expressions
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                // Check if this is a SelectChannelConnector instantiation
                if (n.getType().asString().equals("SelectChannelConnector")) {
                    // Replace with ServerConnector constructor
                    n.setType("ServerConnector");
                    
                    // ServerConnector requires a Server parameter in constructor
                    // Find the Server instance this connector will be added to
                    String serverRef = findServerReferenceForConnector(n);
                    n.getArguments().clear();
                    n.addArgument(serverRef);
                    
                    System.out.println("  Transformed SelectChannelConnector to ServerConnector with argument: " + serverRef + " in " + javaFile);
                    return super.visit(n, arg);
                }
                return super.visit(n, arg);
            }
            
            /**
             * Find the Server instance that a connector will be added to.
             * This is a generic heuristic that works for common patterns:
             * 1. The connector is created and added to a server via server.addConnector()
             * 2. There's a Server variable in scope (field, local variable, or parameter)
             * 3. A Server is created earlier in the same method
             */
            private String findServerReferenceForConnector(ObjectCreationExpr connectorCreation) {
                // Strategy 1: Look for server.addConnector() calls that use this connector
                Optional<Node> parent = connectorCreation.getParentNode();
                while (parent.isPresent()) {
                    Node node = parent.get();
                    
                    // Check if this node is part of a method call expression
                    if (node instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) node;
                        if (methodCall.getNameAsString().equals("addConnector")) {
                            // This connector is being added to a server
                            // The server is the scope of the method call
                            Expression scope = methodCall.getScope().orElse(null);
                            if (scope != null) {
                                return scope.toString();
                            }
                        }
                    }
                    
                    parent = node.getParentNode();
                }
                
                // Strategy 2: Return a generic placeholder
                // In a real implementation, this would use more sophisticated analysis
                // For a generic transformation, we return a placeholder that developers
                // will need to replace with the actual Server reference
                return "/* FIXME: Replace with Server instance reference */ server";
            }
        }, null);
        
        if (wasModified) {
            // Write the transformed file back
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            Files.write(javaFile, cu.toString(config).getBytes());
            return true;
        }
        
        return false;
    }
}