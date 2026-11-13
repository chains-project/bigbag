package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jetty 8/9 code to Jetty 11 in directory: " + sourceDir);
        
        try {
            transformDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(File directory) throws Exception {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        transformDirectory(file);
                    } else if (file.getName().endsWith(".java")) {
                        transformFile(file);
                    }
                }
            }
        }
    }
    
    private static void transformFile(File file) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()));
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + file.getPath())
        );
        
        boolean modified = false;
        
        // Apply all transformations
        Jetty8To11Transformer transformer = new Jetty8To11Transformer();
        cu.accept(transformer, null);
        modified = transformer.isModified();
        
        if (modified) {
            // Write transformed content back to file
            String transformed = new PrettyPrinter().print(cu);
            Files.write(file.toPath(), transformed.getBytes());
            System.out.println("Transformed: " + file.getPath());
        }
    }
    
    static class Jetty8To11Transformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(ImportDeclaration id, Void arg) {
            super.visit(id, arg);
            
            String importName = id.getNameAsString();
            
            // Transform javax.servlet imports to jakarta.servlet
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                id.setName(newImport);
                modified = true;
                System.out.println("  - Changed import: " + importName + " -> " + newImport);
            }
            
            // Transform SelectChannelConnector import
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                id.setName("org.eclipse.jetty.server.ServerConnector");
                modified = true;
                System.out.println("  - Changed import: SelectChannelConnector -> ServerConnector");
            }
        }
        
        @Override
        public void visit(ObjectCreationExpr oce, Void arg) {
            super.visit(oce, arg);
            
            // Check if this is a SelectChannelConnector constructor call
            Optional<Expression> scope = oce.getScope();
            if (!scope.isPresent()) {
                // Check type name
                String typeName = oce.getTypeAsString();
                if (typeName.equals("SelectChannelConnector")) {
                    // Transform to ServerConnector(server) constructor
                    // We need to find the server variable in the context
                    // This is a simplified transformation - in real implementation,
                    // we'd need to track variable declarations
                    oce.setType("ServerConnector");
                    
                    // Add server parameter - this assumes server variable exists in scope
                    // In a complete implementation, we'd need to find the server variable
                    NodeList<Expression> arguments = new NodeList<>();
                    arguments.add(new NameExpr("server"));
                    oce.setArguments(arguments);
                    
                    modified = true;
                    System.out.println("  - Changed constructor: new SelectChannelConnector() -> new ServerConnector(server)");
                }
            }
        }
        
        @Override
        public void visit(MethodCallExpr mce, Void arg) {
            super.visit(mce, arg);
            
            String methodName = mce.getNameAsString();
            Optional<Expression> scope = mce.getScope();
            
            if (scope.isPresent()) {
                String scopeName = scope.get().toString();
                
                // Remove setSendServerVersion and setSendDateHeader calls on Server
                if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                    if (scopeName.equals("server") || scopeName.endsWith(".server")) {
                        // We'll remove this statement by replacing it with null
                        // The parent will need to handle removal
                        System.out.println("  - Found deprecated Server method: " + scopeName + "." + methodName + "()");
                        // Mark for removal - we'll handle in a separate pass
                        mce.replace(new NameExpr("// " + mce.toString() + " - removed for Jetty 11 compatibility"));
                        modified = true;
                    }
                }
                
                // Transform connector.setPort(port) pattern
                if (methodName.equals("setPort") && mce.getArguments().size() == 1) {
                    Expression portArg = mce.getArguments().get(0);
                    
                    // Check if this is called on a connector variable
                    if (scopeName.equals("httpConnector") || scopeName.equals("connector") || 
                        scopeName.endsWith("Connector") || scopeName.contains("connector")) {
                        
                        // We need to change this to constructor-based approach
                        // This requires more context analysis than we can do in a simple visitor
                        System.out.println("  - Found connector.setPort() call: " + scopeName + ".setPort(" + portArg + ")");
                        System.out.println("    Note: This needs to be transformed to ServerConnector constructor parameter");
                        // We can't easily transform this without more context
                        // We'll comment it out as a marker
                        mce.replace(new NameExpr("// TODO: Transform " + mce.toString() + " to ServerConnector constructor parameter"));
                        modified = true;
                    }
                }
                
                // Transform connector.getLocalPort() - this method still exists on ServerConnector
                if (methodName.equals("getLocalPort") && scopeName.contains("connector")) {
                    System.out.println("  - getLocalPort() call on connector: " + scopeName);
                    // No transformation needed - method still exists in Jetty 11
                }
            }
        }
    }
}
