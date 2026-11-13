package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for Jetty 8/9 to Jetty 11 migration.
 * 
 * Breaking changes addressed:
 * 1. SelectChannelConnector -> ServerConnector with HttpConfiguration
 * 2. Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration.setSendServerVersion/setSendDateHeader  
 * 3. javax.servlet -> jakarta.servlet package migration
 * 
 * This transformation can be applied to ANY Maven project affected by these
 * Jetty breaking changes by simply changing the input source directory path.
 */
public class JettyMigrationTransformer {
    
    /**
     * Main entry point for the transformation.
     * 
     * @param args Command line arguments: <source-directory>
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java JettyMigrationTransformer <source-directory>");
            System.err.println("Example: java JettyMigrationTransformer /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jetty 8/9 to Jetty 11 migration transformation to: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                boolean modified = false;
                
                // Apply transformation for javax.servlet -> jakarta.servlet migration
                modified |= transformServletPackageMigration(cu);
                
                // Apply transformation for Jetty connector and configuration changes
                modified |= transformJettyConnectorAndConfiguration(cu);
                
                if (modified) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Successfully transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation completed. " + transformedFiles + " files were updated.");
        System.out.println("\nSummary of changes applied:");
        System.out.println("1. javax.servlet.* imports -> jakarta.servlet.* imports");
        System.out.println("2. SelectChannelConnector -> ServerConnector with HttpConfiguration");
        System.out.println("3. Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration.setSendServerVersion/setSendDateHeader");
    }
    
    /**
     * Transform javax.servlet imports to jakarta.servlet imports.
     * This handles the Servlet API package migration from Java EE to Jakarta EE.
     */
    private static boolean transformServletPackageMigration(CompilationUnit cu) {
        boolean modified = false;
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                importDecl.setName(importName.replace("javax.servlet", "jakarta.servlet"));
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * Transform Jetty connector and configuration breaking changes.
     * Pattern: SelectChannelConnector -> ServerConnector with HttpConfiguration
     * Pattern: Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration.setSendServerVersion/setSendDateHeader
     */
    private static boolean transformJettyConnectorAndConfiguration(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // First, check if this file contains Jetty breaking change patterns
        boolean hasSelectChannelConnector = false;
        boolean hasServerConfigMethods = false;
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                hasSelectChannelConnector = true;
                break;
            }
        }
        
        // Quick scan for server.setSendServerVersion or server.setSendDateHeader
        String cuString = cu.toString();
        if (cuString.contains("setSendServerVersion") || cuString.contains("setSendDateHeader")) {
            hasServerConfigMethods = true;
        }
        
        // Only proceed if we found relevant patterns
        if (!hasSelectChannelConnector && !hasServerConfigMethods) {
            return false;
        }
        
        // Remove old SelectChannelConnector import
        ImportDeclaration toRemove = null;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                toRemove = importDecl;
                break;
            }
        }
        if (toRemove != null) {
            cu.getImports().remove(toRemove);
            modified[0] = true;
        }
        
        // Add required Jetty 11 imports if needed
        addImportIfMissing(cu, "org.eclipse.jetty.server.ServerConnector");
        addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConfiguration");
        addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConnectionFactory");
        
        // Apply transformations to constructors
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ConstructorDeclaration n, Void arg) {
                BlockStmt body = n.getBody();
                if (body != null) {
                    List<Statement> statements = new ArrayList<>(body.getStatements());
                    boolean localModified = false;
                    
                    // Look for the pattern: server.setSendServerVersion/setSendDateHeader calls
                    // and SelectChannelConnector instantiation
                    List<Statement> newStatements = new ArrayList<>();
                    boolean foundServerCreation = false;
                    boolean needsConfiguration = false;
                    List<MethodCallExpr> configCallsToReplace = new ArrayList<>();
                    ObjectCreationExpr selectChannelCreation = null;
                    int selectChannelIndex = -1;
                    
                    for (int i = 0; i < statements.size(); i++) {
                        Statement stmt = statements.get(i);
                        
                        if (stmt instanceof ExpressionStmt) {
                            Expression expr = ((ExpressionStmt) stmt).getExpression();
                            
                            // Check for server.setSendServerVersion(false) or server.setSendDateHeader(true)
                            if (expr instanceof MethodCallExpr) {
                                MethodCallExpr call = (MethodCallExpr) expr;
                                if ((call.getNameAsString().equals("setSendServerVersion") || 
                                     call.getNameAsString().equals("setSendDateHeader")) &&
                                    call.getScope().isPresent() &&
                                    call.getScope().get().toString().equals("server")) {
                                    configCallsToReplace.add(call);
                                    needsConfiguration = true;
                                    localModified = true;
                                    continue; // Skip adding this statement
                                }
                            }
                            
                            // Check for new SelectChannelConnector()
                            if (expr instanceof AssignExpr) {
                                AssignExpr assign = (AssignExpr) expr;
                                if (assign.getValue() instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creation = (ObjectCreationExpr) assign.getValue();
                                    if (creation.getType().getNameAsString().equals("SelectChannelConnector")) {
                                        selectChannelCreation = creation;
                                        selectChannelIndex = i;
                                        localModified = true;
                                        // We'll handle this after processing all statements
                                        continue;
                                    }
                                }
                            }
                            
                            // Check for new Server() to know where to insert HttpConfiguration
                            if (expr.toString().contains("new Server()")) {
                                foundServerCreation = true;
                            }
                        }
                        
                        newStatements.add(stmt);
                    }
                    
                    // If we found patterns to transform, apply the transformations
                    if (localModified) {
                        body.getStatements().clear();
                        
                        // Rebuild statements with transformations
                        for (int i = 0; i < newStatements.size(); i++) {
                            Statement stmt = newStatements.get(i);
                            
                            // Insert HttpConfiguration after server creation if needed
                            if (needsConfiguration && foundServerCreation && 
                                stmt.toString().contains("new Server()")) {
                                body.addStatement(stmt);
                                
                                // Add HttpConfiguration creation
                                body.addStatement(createHttpConfigurationDeclaration());
                                body.addStatement(createHttpConfigurationAssignment());
                                
                                // Add configuration method calls
                                for (MethodCallExpr oldCall : configCallsToReplace) {
                                    MethodCallExpr newCall = new MethodCallExpr(
                                        new NameExpr("configuration"),
                                        oldCall.getNameAsString(),
                                        oldCall.getArguments()
                                    );
                                    body.addStatement(new ExpressionStmt(newCall));
                                }
                                
                                continue;
                            }
                            
                            // Replace SelectChannelConnector with ServerConnector if this is that statement
                            if (i == selectChannelIndex && selectChannelCreation != null) {
                                ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                                AssignExpr assign = (AssignExpr) exprStmt.getExpression();
                                
                                // Replace with ServerConnector
                                ObjectCreationExpr serverConnectorCreation = new ObjectCreationExpr(
                                    null,
                                    new ClassOrInterfaceType("ServerConnector"),
                                    new NodeList<>(
                                        new NameExpr("server"),
                                        new ObjectCreationExpr(
                                            null,
                                            new ClassOrInterfaceType("HttpConnectionFactory"),
                                            new NodeList<>(new NameExpr("configuration"))
                                        )
                                    )
                                );
                                
                                AssignExpr newAssign = new AssignExpr(
                                    assign.getTarget(),
                                    serverConnectorCreation,
                                    assign.getOperator()
                                );
                                
                                body.addStatement(new ExpressionStmt(newAssign));
                                continue;
                            }
                            
                            body.addStatement(stmt);
                        }
                        
                        modified[0] = true;
                    }
                }
                return super.visit(n, arg);
            }
        }, null);
        
        return modified[0];
    }
    
    /**
     * Create HttpConfiguration variable declaration statement.
     */
    private static ExpressionStmt createHttpConfigurationDeclaration() {
        return new ExpressionStmt(
            new VariableDeclarationExpr(
                new ClassOrInterfaceType("HttpConfiguration"),
                "configuration"
            )
        );
    }
    
    /**
     * Create HttpConfiguration assignment statement.
     */
    private static ExpressionStmt createHttpConfigurationAssignment() {
        return new ExpressionStmt(
            new AssignExpr(
                new NameExpr("configuration"),
                new ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType("HttpConfiguration"),
                    new NodeList<>()
                ),
                AssignExpr.Operator.ASSIGN
            )
        );
    }
    
    /**
     * Add import if not already present.
     */
    private static void addImportIfMissing(CompilationUnit cu, String importName) {
        boolean hasImport = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals(importName)) {
                hasImport = true;
                break;
            }
        }
        if (!hasImport) {
            cu.addImport(importName);
        }
    }
}