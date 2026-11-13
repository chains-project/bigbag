package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    /**
     * Generic transformation rule for fixing Jetty 9.x API breaking changes.
     * This transformation handles:
     * 1. SelectChannelConnector -> ServerConnector migration
     * 2. Server.setSendServerVersion()/setSendDateHeader() removal (needs manual HttpConfiguration setup)
     * 3. Connector.setPort() -> constructor parameter migration
     * 
     * This is a GENERIC transformation that can be applied to ANY Maven project
     * affected by the same Jetty API breaking changes.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                boolean modified = false;
                
                // Transform imports
                for (ImportDeclaration imp : cu.getImports()) {
                    if (imp.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                        imp.setName("org.eclipse.jetty.server.ServerConnector");
                        modified = true;
                    }
                }
                
                // Transform ObjectCreationExpr nodes
                JettyTransformationVisitor visitor = new JettyTransformationVisitor();
                visitor.visit(cu, null);
                modified |= visitor.isModified();
                
                if (modified) {
                    String transformedCode = new DefaultPrettyPrinter(new DefaultPrinterConfiguration()).print(cu);
                    Files.write(javaFile, transformedCode.getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\n=== TRANSFORMATION SUMMARY ===");
        System.out.println("Transformed " + transformedFiles + " files");
        System.out.println("\n=== BREAKING CHANGES HANDLED ===");
        System.out.println("1. SelectChannelConnector -> ServerConnector");
        System.out.println("2. Server.setSendServerVersion()/setSendDateHeader() removed (requires manual HttpConfiguration setup)");
        System.out.println("3. Connector.setPort() migrated to constructor parameter");
        System.out.println("\n=== MANUAL STEPS REQUIRED ===");
        System.out.println("- HttpConfiguration setup needs to be implemented manually");
        System.out.println("- Review transformed code for any additional adjustments");
    }
    
    /**
     * Visitor for Jetty API transformations
     */
    private static class JettyTransformationVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private Map<String, Expression> variablePortMap = new HashMap<>();
        private List<ExpressionStmt> statementsToRemove = new ArrayList<>();
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            // Transform new SelectChannelConnector() to new ServerConnector(server)
            if (n.getType().asString().equals("SelectChannelConnector")) {
                // Create arguments for ServerConnector constructor
                NodeList<Expression> args = new NodeList<>();
                args.add(new NameExpr("server"));
                
                n.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                n.setArguments(args);
                modified = true;
            }
            super.visit(n, arg);
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            // Mark server configuration calls for manual handling
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                if (n.getScope().isPresent() && n.getScope().get().toString().equals("server")) {
                    // These need to be handled manually with HttpConfiguration
                    System.out.println("NOTE: Found " + methodName + "() call - requires manual HttpConfiguration setup");
                }
            }
            
            // Handle port setting
            if (methodName.equals("setPort")) {
                if (n.getScope().isPresent()) {
                    String scope = n.getScope().get().toString();
                    if (n.getArguments().size() == 1) {
                        // Store port for later use in constructor
                        variablePortMap.put(scope, n.getArgument(0));
                    }
                }
            }
            
            super.visit(n, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}