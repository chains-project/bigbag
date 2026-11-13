package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JettyMigrationTool {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jetty-migration.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Jetty APIs for migration to Jetty 11 in: " + sourceDir);
        
        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        int transformedFiles = 0;
        
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                if (!parseResult.isSuccessful()) {
                    System.err.println("Error parsing file: " + javaFile);
                    continue;
                }
                
                Optional<CompilationUnit> cuOpt = parseResult.getResult();
                if (!cuOpt.isPresent()) {
                    System.err.println("No compilation unit for file: " + javaFile);
                    continue;
                }
                
                CompilationUnit cu = cuOpt.get();
                JettyMigrationTransformer transformer = new JettyMigrationTransformer();
                cu.accept(transformer, null);
                int changes = transformer.getTransformationCount();
                
                if (changes > 0) {
                    transformedFiles++;
                    
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(cu.toString());
                    }
                    
                    System.out.println("Transformed " + changes + " Jetty API occurrences in: " + sourceDir.relativize(javaFile));
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
    }
    
    static class JettyMigrationTransformer extends GenericVisitorAdapter<Void, Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Void visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Remove old nio package imports
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.remove();
                transformationCount++;
                System.out.println("  Removed deprecated import: " + importName);
            }
            // Add ServerConnector import if SelectChannelConnector was used
            else if (importName.equals("org.eclipse.jetty.server.Server")) {
                // Check if we should add ServerConnector import
                importDecl.getParentNode().ifPresent(parent -> {
                    if (parent instanceof CompilationUnit) {
                        CompilationUnit cu = (CompilationUnit) parent;
                        boolean hasServerConnectorImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"));
                        if (!hasServerConnectorImport) {
                            cu.addImport("org.eclipse.jetty.server.ServerConnector");
                            transformationCount++;
                            System.out.println("  Added import: org.eclipse.jetty.server.ServerConnector");
                        }
                    }
                });
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Void visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Replace SelectChannelConnector with ServerConnector in type references
            if (typeName.equals("SelectChannelConnector")) {
                type.setName("ServerConnector");
                transformationCount++;
                System.out.println("  Type: SelectChannelConnector -> ServerConnector");
            }
            
            return super.visit(type, arg);
        }
        
        @Override
        public Void visit(ObjectCreationExpr expr, Void arg) {
            // Replace new SelectChannelConnector() with new ServerConnector(server)
            if (expr.getType().getNameAsString().equals("SelectChannelConnector")) {
                expr.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                transformationCount++;
                System.out.println("  Object creation: new SelectChannelConnector() -> new ServerConnector(server)");
                
                // Check if we need to add server parameter
                // This is complex and depends on context, so we'll just note it
                System.out.println("    Note: ServerConnector requires Server parameter in constructor");
            }
            
            return super.visit(expr, arg);
        }
        
        @Override
        public Void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Remove deprecated Server methods
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                Node parent = expr.getParentNode().orElse(null);
                if (parent instanceof ExpressionStmt) {
                    parent.remove();
                    transformationCount++;
                    System.out.println("  Removed deprecated method call: " + methodName);
                    System.out.println("    Note: These methods are no longer available in Jetty 11");
                    System.out.println("    Note: Configure via HttpConfiguration instead");
                }
            }
            // Replace connector.setPort() with serverConnector.setPort()
            else if (methodName.equals("setPort")) {
                if (expr.getScope().isPresent()) {
                    Expression scope = expr.getScope().get();
                    if (scope.toString().equals("httpConnector")) {
                        System.out.println("  Method call: httpConnector.setPort() - should be serverConnector.setPort()");
                        System.out.println("    Note: ServerConnector has setPort() method");
                    }
                }
            }
            // Replace connector.getLocalPort() with serverConnector.getLocalPort()
            else if (methodName.equals("getLocalPort")) {
                if (expr.getScope().isPresent()) {
                    Expression scope = expr.getScope().get();
                    if (scope.toString().equals("httpConnector")) {
                        System.out.println("  Method call: httpConnector.getLocalPort() - should be serverConnector.getLocalPort()");
                        System.out.println("    Note: ServerConnector has getLocalPort() method");
                    }
                }
            }
            
            return super.visit(expr, arg);
        }
        
        @Override
        public Void visit(FieldDeclaration field, Void arg) {
            // Update field types from SelectChannelConnector to ServerConnector
            for (VariableDeclarator var : field.getVariables()) {
                if (var.getType().toString().equals("SelectChannelConnector")) {
                    var.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    transformationCount++;
                    System.out.println("  Field type: SelectChannelConnector -> ServerConnector");
                } else if (var.getType().toString().equals("Connector") && 
                          field.getParentNode().isPresent() &&
                          field.getParentNode().get() instanceof ClassOrInterfaceDeclaration) {
                    // Check if this Connector field is assigned with SelectChannelConnector
                    ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration) field.getParentNode().get();
                    System.out.println("  Connector field '" + var.getName() + "' in class " + clazz.getName());
                    System.out.println("    Note: May need to change from Connector to ServerConnector type");
                }
            }
            
            return super.visit(field, arg);
        }
        
        @Override
        public Void visit(ClassOrInterfaceDeclaration clazz, Void arg) {
            // Add guidance for class-level changes
            boolean hasJettyReferences = clazz.getMembers().stream()
                .anyMatch(member -> member.toString().contains("jetty") || 
                                   member.toString().contains("Jetty") ||
                                   member.toString().contains("SelectChannelConnector") ||
                                   member.toString().contains("ServerConnector"));
            
            if (hasJettyReferences) {
                System.out.println("  Class " + clazz.getName() + " uses Jetty APIs");
                System.out.println("    Migration notes for Jetty 11:");
                System.out.println("    1. SelectChannelConnector -> ServerConnector");
                System.out.println("    2. Server.setSendServerVersion()/setSendDateHeader() removed");
                System.out.println("    3. Configure via HttpConfiguration instead");
                System.out.println("    4. javax.servlet -> jakarta.servlet package change");
            }
            
            return super.visit(clazz, arg);
        }
    }
}