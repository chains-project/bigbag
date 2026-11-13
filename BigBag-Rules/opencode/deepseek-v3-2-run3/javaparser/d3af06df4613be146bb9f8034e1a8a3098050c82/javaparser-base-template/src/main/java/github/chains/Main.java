package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Generic transformation rule for Jetty 8/9 to Jetty 11 API migration.
 * 
 * BREAKING CHANGES ADDRESSED:
 * 1. Package migration: javax.servlet -> jakarta.servlet
 * 2. Class removal: SelectChannelConnector -> ServerConnector replacement
 * 3. API relocation: Server configuration methods -> HttpConfiguration
 * 4. Interface change: Connector methods -> NetworkConnector interface
 * 
 * USAGE: java -jar javaparser.jar <source-directory>
 * 
 * This transformation applies automatic fixes for import statements and
 * simple class replacements, and provides guidance for complex API migrations.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Applies Jetty 8/9 to Jetty 11 migration transformations");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        List<String> guidanceNotes = new ArrayList<>();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                JettyMigrationTransformer transformer = new JettyMigrationTransformer();
                cu.accept(transformer, null);
                
                if (transformer.isModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                    
                    // Collect guidance for this file
                    if (transformer.needsManualConfiguration()) {
                        guidanceNotes.add(javaFile + ": Needs manual HttpConfiguration setup");
                    }
                    if (transformer.hasPortConfiguration()) {
                        guidanceNotes.add(javaFile + ": Port configuration may need NetworkConnector cast");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        // Output transformation summary
        System.out.println("\n=== JETTY 11 MIGRATION TRANSFORMATION COMPLETE ===");
        System.out.println("Transformed " + transformedFiles + " file(s)");
        
        System.out.println("\n=== AUTOMATIC CHANGES APPLIED ===");
        System.out.println("1. Updated javax.servlet.* imports to jakarta.servlet.*");
        System.out.println("2. Replaced SelectChannelConnector with ServerConnector");
        System.out.println("3. Added necessary Jetty 11 imports");
        
        System.out.println("\n=== MANUAL CHANGES REQUIRED ===");
        System.out.println("The following API changes require manual code adjustments:");
        
        System.out.println("\n1. SERVER CONFIGURATION:");
        System.out.println("   OLD: server.setSendServerVersion(false)");
        System.out.println("   OLD: server.setSendDateHeader(true)");
        System.out.println("   NEW: Configure via HttpConfiguration:");
        System.out.println("        HttpConfiguration httpConfig = new HttpConfiguration();");
        System.out.println("        httpConfig.setSendServerVersion(false);");
        System.out.println("        httpConfig.setSendDateHeader(true);");
        System.out.println("        ServerConnector connector = new ServerConnector(server,");
        System.out.println("            new HttpConnectionFactory(httpConfig));");
        
        System.out.println("\n2. PORT CONFIGURATION:");
        System.out.println("   OLD: connector.setPort(port)");
        System.out.println("   NEW: ((NetworkConnector) connector).setPort(port)");
        System.out.println("   OR:  Pass port to ServerConnector constructor");
        
        System.out.println("\n3. GET LOCAL PORT:");
        System.out.println("   OLD: connector.getLocalPort()");
        System.out.println("   NEW: ((NetworkConnector) connector).getLocalPort()");
        
        System.out.println("\n4. CONNECTOR CREATION:");
        System.out.println("   OLD: new SelectChannelConnector()");
        System.out.println("   NEW: new ServerConnector(server, new HttpConnectionFactory(httpConfig))");
        
        if (!guidanceNotes.isEmpty()) {
            System.out.println("\n=== FILE-SPECIFIC GUIDANCE ===");
            for (String note : guidanceNotes) {
                System.out.println("  - " + note);
            }
        }
        
        System.out.println("\n=== NEXT STEPS ===");
        System.out.println("1. Review transformed files for compilation errors");
        System.out.println("2. Apply manual fixes based on guidance above");
        System.out.println("3. Update Maven dependencies to Jetty 11.0.8");
        System.out.println("4. Run tests to ensure functionality is preserved");
    }
    
    private static class JettyMigrationTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private boolean hasServerConfig = false;
        private boolean hasPortConfig = false;
        
        public boolean isModified() {
            return modified;
        }
        
        public boolean needsManualConfiguration() {
            return hasServerConfig;
        }
        
        public boolean hasPortConfiguration() {
            return hasPortConfig;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Transform javax.servlet to jakarta.servlet
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                modified = true;
                System.out.println("  [INFO] Updated import: " + importName + " -> " + newImport);
            }
            
            // Transform SelectChannelConnector import
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                modified = true;
                System.out.println("  [INFO] Replaced SelectChannelConnector import with ServerConnector");
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(CompilationUnit cu, Void arg) {
            Node result = (Node) super.visit(cu, arg);
            
            // Add necessary Jetty 11 imports if we find relevant code patterns
            boolean hasConnectorCode = cu.toString().contains("Connector");
            boolean hasServletCode = cu.toString().contains("javax.servlet") || 
                                   cu.toString().contains("jakarta.servlet");
            
            if (hasConnectorCode && !hasImport(cu, "org.eclipse.jetty.server.NetworkConnector")) {
                cu.addImport("org.eclipse.jetty.server.NetworkConnector");
                modified = true;
            }
            
            if (hasConnectorCode && !hasImport(cu, "org.eclipse.jetty.server.HttpConfiguration")) {
                cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
                modified = true;
            }
            
            if (hasConnectorCode && !hasImport(cu, "org.eclipse.jetty.server.HttpConnectionFactory")) {
                cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
                modified = true;
            }
            
            return result;
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Replace new SelectChannelConnector() with new ServerConnector(server)
            String typeName = expr.getType().toString();
            if (typeName.equals("SelectChannelConnector")) {
                ObjectCreationExpr newExpr = new ObjectCreationExpr();
                newExpr.setType("ServerConnector");
                
                // Create basic constructor call - developer will need to add HttpConnectionFactory
                NameExpr serverArg = new NameExpr("server");
                newExpr.setArguments(new NodeList<>(serverArg));
                
                modified = true;
                hasServerConfig = true; // Flag that manual configuration is needed
                System.out.println("  [INFO] Replaced SelectChannelConnector creation (needs HttpConnectionFactory)");
                return newExpr;
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Detect server configuration methods that need manual migration
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                Optional<Expression> scope = expr.getScope();
                if (scope.isPresent() && scope.get().toString().contains("server")) {
                    hasServerConfig = true;
                    System.out.println("  [NOTE] Found " + methodName + " call - needs HttpConfiguration migration");
                }
            }
            
            // Detect port configuration methods
            if (methodName.equals("setPort") || methodName.equals("getLocalPort")) {
                Optional<Expression> scope = expr.getScope();
                if (scope.isPresent()) {
                    String scopeStr = scope.get().toString();
                    if (scopeStr.matches(".*[Cc]onnector.*")) {
                        hasPortConfig = true;
                        
                        // Try to add cast for simple cases
                        if (methodName.equals("getLocalPort")) {
                            // Create cast expression for getLocalPort()
                            CastExpr castExpr = new CastExpr();
                            ClassOrInterfaceType networkConnectorType = new ClassOrInterfaceType();
                            networkConnectorType.setName("NetworkConnector");
                            castExpr.setType(networkConnectorType);
                            castExpr.setExpression(scope.get().clone());
                            
                            MethodCallExpr newCall = new MethodCallExpr();
                            newCall.setScope(castExpr);
                            newCall.setName("getLocalPort");
                            
                            modified = true;
                            System.out.println("  [INFO] Added NetworkConnector cast for getLocalPort()");
                            return newCall;
                        }
                    }
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        private boolean hasImport(CompilationUnit cu, String importName) {
            return cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(importName));
        }
    }
}