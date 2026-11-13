package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.Node;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("This tool transforms Jetty 8/9 code to work with Jetty 11+");
            System.err.println("Transformations:");
            System.err.println("  1. javax.servlet -> jakarta.servlet imports");
            System.err.println("  2. SelectChannelConnector -> ServerConnector with HttpConfiguration");
            System.err.println("  3. server.setSendServerVersion/setSendDateHeader -> httpConfig.setSendServerVersion/setSendDateHeader");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Starting transformation of Jetty 8/9 to Jetty 11+");
        System.out.println("Source directory: " + sourceDir.toAbsolutePath());
        
        // Process all Java files
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(Main::transformFile);
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(content);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                boolean changed = false;
                
                // Apply servlet import transformation
                ServletImportTransformer servletTransformer = new ServletImportTransformer();
                CompilationUnit result1 = (CompilationUnit) servletTransformer.visit(cu, null);
                if (servletTransformer.wasChanged()) {
                    changed = true;
                    cu = result1;
                }
                
                // Apply SelectChannelConnector transformation
                SelectChannelConnectorTransformer connectorTransformer = new SelectChannelConnectorTransformer();
                CompilationUnit result2 = (CompilationUnit) connectorTransformer.visit(cu, null);
                if (connectorTransformer.wasChanged()) {
                    changed = true;
                    cu = result2;
                }
                
                // Apply Server configuration transformation
                ServerConfigTransformer configTransformer = new ServerConfigTransformer();
                CompilationUnit result3 = (CompilationUnit) configTransformer.visit(cu, null);
                if (configTransformer.wasChanged()) {
                    changed = true;
                    cu = result3;
                }
                
                // Only write back if changes were made
                if (changed) {
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String transformed = printer.print(cu);
                    Files.writeString(filePath, transformed);
                    System.out.println("✓ Transformed: " + filePath.getFileName());
                } else {
                    System.out.println("  No changes needed: " + filePath.getFileName());
                }
            } else {
                System.err.println("✗ Failed to parse: " + filePath.getFileName());
                parseResult.getProblems().forEach(p -> System.err.println("  " + p.toString()));
            }
        } catch (IOException e) {
            System.err.println("✗ Error reading/writing: " + filePath + " - " + e.getMessage());
        } catch (Exception e) {
            System.err.println("✗ Error transforming: " + filePath + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Transformation 1: Convert javax.servlet imports to jakarta.servlet
     */
    private static class ServletImportTransformer extends ModifierVisitor<Void> {
        private boolean changed = false;
        
        public boolean wasChanged() { return changed; }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                id.setName(newImport);
                changed = true;
                System.out.println("    - Changed import: " + importName + " -> " + newImport);
            }
            return (Node) super.visit(id, arg);
        }
    }
    
    /**
     * Transformation 2: Replace SelectChannelConnector with ServerConnector
     * and add HttpConfiguration setup
     */
    private static class SelectChannelConnectorTransformer extends ModifierVisitor<Void> {
        private boolean changed = false;
        private boolean hasSelectChannelImport = false;
        
        public boolean wasChanged() { return changed; }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                hasSelectChannelImport = true;
                // Remove this import - it will be replaced
                changed = true;
                System.out.println("    - Removing import: " + importName);
                return null;
            }
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            if (typeName.equals("SelectChannelConnector")) {
                changed = true;
                System.out.println("    - Transforming: new SelectChannelConnector() -> new ServerConnector(...)");
                
                // Get the compilation unit for adding imports
                CompilationUnit cu = expr.findCompilationUnit().orElse(null);
                if (cu != null) {
                    // Add required imports for Jetty 11
                    addImportIfMissing(cu, "org.eclipse.jetty.server.ServerConnector");
                    addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConfiguration");
                    addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConnectionFactory");
                }
                
                // Transform to: new ServerConnector(server, new HttpConnectionFactory(httpConfig))
                expr.setType("ServerConnector");
                
                // Clear existing arguments
                expr.getArguments().clear();
                
                // Add server as first argument
                expr.addArgument(new NameExpr("server"));
                
                // Create HttpConnectionFactory with httpConfig
                ObjectCreationExpr httpFactory = new ObjectCreationExpr();
                httpFactory.setType("HttpConnectionFactory");
                httpFactory.addArgument(new NameExpr("httpConfig"));
                
                // Add as second argument
                expr.addArgument(httpFactory);
                
                // Note: The caller needs to ensure httpConfig variable is created
                // with proper initialization: httpConfig.setSendServerVersion(false) etc.
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(CompilationUnit cu, Void arg) {
            Node result = (Node) super.visit(cu, arg);
            
            // After visiting all nodes, if we removed SelectChannelConnector import
            // we should add the new imports
            if (hasSelectChannelImport) {
                addImportIfMissing(cu, "org.eclipse.jetty.server.ServerConnector");
                addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConfiguration");
                addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConnectionFactory");
            }
            
            return result;
        }
        
        private void addImportIfMissing(CompilationUnit cu, String importName) {
            boolean hasImport = cu.getImports().stream()
                .anyMatch(id -> id.getNameAsString().equals(importName));
            if (!hasImport) {
                cu.addImport(importName);
                System.out.println("    - Adding import: " + importName);
            }
        }
    }
    
    /**
     * Transformation 3: Handle Server API method changes
     * server.setSendServerVersion() and server.setSendDateHeader() moved to HttpConfiguration
     */
    private static class ServerConfigTransformer extends ModifierVisitor<Void> {
        private boolean changed = false;
        private boolean needsHttpConfig = false;
        
        public boolean wasChanged() { return changed; }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Check for methods that moved from Server to HttpConfiguration
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                Optional<Expression> scopeOpt = expr.getScope();
                if (scopeOpt.isPresent()) {
                    Expression scope = scopeOpt.get();
                    String scopeName = scope.toString();
                    
                    // If it's called on a server variable
                    if (scopeName.equals("server")) {
                        changed = true;
                        needsHttpConfig = true;
                        
                        System.out.println("    - Moving method: server." + methodName + 
                                          "() -> httpConfig." + methodName + "()");
                        
                        // Change scope from server to httpConfig
                        expr.setScope(new NameExpr("httpConfig"));
                        
                        // Add HttpConfiguration import if needed
                        CompilationUnit cu = expr.findCompilationUnit().orElse(null);
                        if (cu != null) {
                            addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConfiguration");
                        }
                    }
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(CompilationUnit cu, Void arg) {
            Node result = (Node) super.visit(cu, arg);
            
            // If we need httpConfig, check if we should add initialization
            if (needsHttpConfig) {
                // This is a simplified check - in a real implementation,
                // we would analyze the code structure to find where to insert
                // HttpConfiguration httpConfig = new HttpConfiguration();
                System.out.println("    - Note: Need to initialize HttpConfiguration httpConfig = new HttpConfiguration()");
            }
            
            return result;
        }
        
        private void addImportIfMissing(CompilationUnit cu, String importName) {
            boolean hasImport = cu.getImports().stream()
                .anyMatch(id -> id.getNameAsString().equals(importName));
            if (!hasImport) {
                cu.addImport(importName);
            }
        }
    }
}