package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Jetty11Transformation {
    
    public static class Jetty11Visitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            String importName = n.getNameAsString();
            
            // Transform javax.servlet -> jakarta.servlet
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                n.setName(newImport);
                modified = true;
            }
            
            // Transform org.eclipse.jetty.server.nio.SelectChannelConnector
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                n.setName("org.eclipse.jetty.server.ServerConnector");
                modified = true;
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            
            // Update type references
            String typeName = n.getNameAsString();
            
            if (typeName.equals("SelectChannelConnector")) {
                n.setName("ServerConnector");
                modified = true;
            }
        }
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            String typeName = n.getType().getNameAsString();
            
            if (typeName.equals("SelectChannelConnector")) {
                // Replace SelectChannelConnector() with ServerConnector(server)
                n.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                
                // Add server as argument if no arguments
                if (n.getArguments().isEmpty()) {
                    n.getArguments().add(new NameExpr("server"));
                }
                modified = true;
            }
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            String methodName = n.getNameAsString();
            
            // Handle server.setSendServerVersion() and server.setSendDateHeader()
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                // These need to be moved to HttpConfiguration
                n.setName(methodName + " /* TODO: Move to HttpConfiguration */");
                modified = true;
            }
            
            // Handle connector.setPort()
            if (methodName.equals("setPort")) {
                // setPort() should be removed - port is set in ServerConnector constructor
                n.setName("setPort /* TODO: Set port in ServerConnector constructor */");
                modified = true;
            }
        }
        
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Fix AbstractHandler.handle() method signature
            if (n.getNameAsString().equals("handle")) {
                NodeList<Parameter> params = n.getParameters();
                if (params.size() >= 4) {
                    // Check and update parameter types from javax.servlet to jakarta.servlet
                    for (Parameter param : params) {
                        Type paramType = param.getType();
                        if (paramType instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType type = (ClassOrInterfaceType) paramType;
                            String typeName = type.getNameAsString();
                            
                            // Update unqualified servlet types
                            if (typeName.equals("HttpServletRequest") || typeName.equals("HttpServletResponse") || 
                                typeName.equals("ServletException")) {
                                if (!type.getScope().isPresent()) {
                                    // Add jakarta.servlet package
                                    String pkg = typeName.equals("ServletException") ? "jakarta.servlet" : "jakarta.servlet.http";
                                    type.setName(pkg + "." + typeName);
                                    modified = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    public static void transformFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            Jetty11Visitor visitor = new Jetty11Visitor();
            visitor.visit(cu, null);
            
            if (visitor.wasModified()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Jetty11Transformation <source-directory>");
            System.err.println("Example: java Jetty11Transformation /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jetty 9/10 code to Jetty 11 in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedCount = 0;
        for (Path filePath : javaFiles) {
            try {
                transformFile(filePath);
                transformedCount++;
            } catch (Exception e) {
                System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedCount + " files");
        
        // Print summary of transformations needed
        System.out.println("\n=== Jetty 11 Migration Summary ===");
        System.out.println("Applied transformations:");
        System.out.println("1. javax.servlet -> jakarta.servlet package change");
        System.out.println("2. SelectChannelConnector -> ServerConnector replacement");
        System.out.println("3. Added TODO comments for manual fixes:");
        System.out.println("   - Server.setSendServerVersion()/setSendDateHeader() -> HttpConfiguration methods");
        System.out.println("   - Connector.setPort() -> Set port in ServerConnector constructor");
        System.out.println("   - AbstractHandler.handle() method signature updated");
        System.out.println("\nNote: Manual review required for:");
        System.out.println("- Server configuration (needs HttpConfiguration object)");
        System.out.println("- SelectChannelConnector() constructor (needs server reference)");
        System.out.println("- Verify all imports are updated correctly");
    }
}