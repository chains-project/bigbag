package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser javaParser = new JavaParser();
        int processedFiles = 0;
        int modifiedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                // Apply transformations
                JettyMigrationTransformer transformer = new JettyMigrationTransformer();
                CompilationUnit transformedCu = (CompilationUnit) transformer.visit(cu, null);
                
                if (transformer.wasModified()) {
                    Files.write(javaFile, transformedCu.toString().getBytes());
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
                
                processedFiles++;
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Processed " + processedFiles + " files, modified " + modifiedFiles + " files");
    }
    
    /**
     * Transformer for Jetty 9 to Jetty 11 migration
     */
    static class JettyMigrationTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Transform javax.servlet imports to jakarta.servlet
            if (importName.startsWith("javax.servlet")) {
                String newName = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newName);
                modified = true;
                System.out.println("Transformed import: " + importName + " -> " + newName);
            }
            
            // Transform SelectChannelConnector import to ServerConnector
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                modified = true;
                System.out.println("Transformed import: SelectChannelConnector -> ServerConnector");
            }
            
            return importDecl;
        }
        
        @Override
        public ClassOrInterfaceType visit(ClassOrInterfaceType type, Void arg) {
            ClassOrInterfaceType visitedType = (ClassOrInterfaceType) super.visit(type, arg);
            
            // Handle javax.servlet type references (e.g., javax.servlet.http.HttpServletRequest)
            if (visitedType.getScope().isPresent()) {
                String scope = visitedType.getScope().get().asString();
                if (scope.startsWith("javax.servlet")) {
                    String newScope = scope.replace("javax.servlet", "jakarta.servlet");
                    // Create a new type with updated scope
                    visitedType.setScope(new ClassOrInterfaceType(null, newScope));
                    modified = true;
                    System.out.println("Transformed type: " + scope + "." + visitedType.getNameAsString() + 
                                     " -> " + newScope + "." + visitedType.getNameAsString());
                }
            }
            
            // Handle SelectChannelConnector type references
            if (visitedType.getNameAsString().equals("SelectChannelConnector")) {
                visitedType.setName("ServerConnector");
                modified = true;
                System.out.println("Transformed type: SelectChannelConnector -> ServerConnector");
            }
            
            return visitedType;
        }
        
        @Override
        public ObjectCreationExpr visit(ObjectCreationExpr expr, Void arg) {
            ObjectCreationExpr visitedExpr = (ObjectCreationExpr) super.visit(expr, arg);
            
            // Handle new SelectChannelConnector()
            if (visitedExpr.getType().getNameAsString().equals("SelectChannelConnector")) {
                // Try to find the server variable in the containing class
                Optional<ClassOrInterfaceDeclaration> containingClass = visitedExpr.findAncestor(ClassOrInterfaceDeclaration.class);
                String serverVarName = "server"; // default
                
                if (containingClass.isPresent()) {
                    // Look for a Server field
                    Optional<FieldDeclaration> serverField = containingClass.get().getFields().stream()
                        .filter(f -> f.getVariables().stream()
                            .anyMatch(v -> v.getTypeAsString().contains("Server")))
                        .findFirst();
                    
                    if (serverField.isPresent()) {
                        serverVarName = serverField.get().getVariables().get(0).getNameAsString();
                    }
                }
                
                // Create new ServerConnector(server)
                visitedExpr.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                NodeList<Expression> args = new NodeList<>();
                args.add(new NameExpr(serverVarName));
                visitedExpr.setArguments(args);
                
                modified = true;
                System.out.println("Transformed constructor: new SelectChannelConnector() -> new ServerConnector(" + serverVarName + ")");
            }
            
            return visitedExpr;
        }
        
        @Override
        public MethodCallExpr visit(MethodCallExpr methodCall, Void arg) {
            MethodCallExpr visitedCall = (MethodCallExpr) super.visit(methodCall, arg);
            String methodName = visitedCall.getNameAsString();
            
            // Handle Server.setSendServerVersion() and Server.setSendDateHeader()
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                if (visitedCall.getScope().isPresent()) {
                    Expression scope = visitedCall.getScope().get();
                    String scopeStr = scope.toString();
                    
                    // Check if it's called on a Server instance
                    if (scopeStr.contains("server") || scopeStr.contains("new Server()")) {
                        // These methods are removed in Jetty 11
                        // Add a comment before the statement
                        Optional<ExpressionStmt> parentStmt = visitedCall.findAncestor(ExpressionStmt.class);
                        if (parentStmt.isPresent()) {
                            String commentText = "// FIXME: " + methodName + "() removed in Jetty 11 - use HttpConfiguration instead";
                            parentStmt.get().setLineComment(commentText);
                            System.out.println("Added fixme comment for " + methodName + "()");
                            modified = true;
                        }
                    }
                }
            }
            
            return visitedCall;
        }
        
        boolean wasModified() {
            return modified;
        }
    }
}