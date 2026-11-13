package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser javaParser = new JavaParser();
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                boolean modified = false;
                
                // Transform javax.servlet imports to jakarta.servlet
                for (ImportDeclaration importDecl : cu.getImports()) {
                    String importName = importDecl.getNameAsString();
                    if (importName.startsWith("javax.servlet")) {
                        String newImportName = importName.replace("javax.servlet", "jakarta.servlet");
                        importDecl.setName(newImportName);
                        modified = true;
                        System.out.println("Updated import: " + importName + " -> " + newImportName);
                    }
                }
                
                // Visit and transform SelectChannelConnector to ServerConnector
                // Also handle other Jetty API changes
                JettyMigrationVisitor visitor = new JettyMigrationVisitor();
                CompilationUnit transformedCu = (CompilationUnit) cu.accept(visitor, null);
                if (visitor.wasModified()) {
                    modified = true;
                }
                
                if (modified) {
                    // Write back the modified file
                    String newContent = transformedCu.toString();
                    Files.write(javaFile, newContent.getBytes());
                    filesModified++;
                    System.out.println("Modified: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Processing complete. Modified " + filesModified + " files.");
    }
    
    static class JettyMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Transform SelectChannelConnector to ServerConnector
            if (n.getType().asString().equals("SelectChannelConnector")) {
                modified = true;
                
                // Replace SelectChannelConnector type with ServerConnector
                n.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                
                // Get constructor arguments
                NodeList<Expression> arguments = n.getArguments();
                
                // Pattern 1: new SelectChannelConnector() 
                // Should become: new ServerConnector(server) where 'server' is a Server instance
                // We can't automatically find the Server instance, so we add a TODO
                if (arguments.isEmpty()) {
                    arguments.add(new NameExpr("/* TODO: Add Server instance parameter */ server"));
                } 
                // Pattern 2: new SelectChannelConnector(port)
                // Should become: new ServerConnector(server) and port handled separately
                else if (arguments.size() == 1) {
                    // Keep the port argument as a comment for manual handling
                    Expression portArg = arguments.get(0);
                    arguments.set(0, new NameExpr("/* TODO: Add Server instance, port was: " + portArg + " */ server"));
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Update type references
            String typeName = n.getNameAsString();
            if (typeName.equals("SelectChannelConnector")) {
                modified = true;
                n.setName("ServerConnector");
            }
            // Also check for fully qualified names
            else if (typeName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                modified = true;
                n.setName("org.eclipse.jetty.server.ServerConnector");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for method calls that might need transformation
            String methodName = n.getNameAsString();
            
            // Handle server.addConnector(connector) - ServerConnector might not need this
            // or might handle it differently
            if (methodName.equals("addConnector") && n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                // If adding a connector to a server, ServerConnector might already be associated
                // via constructor. We'll leave as is for now.
            }
            
            // Check for javax.servlet API method calls that might need updating
            // (though import transformation should handle most cases)
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check for field accesses that might need updating
            return super.visit(n, arg);
        }
    }
}