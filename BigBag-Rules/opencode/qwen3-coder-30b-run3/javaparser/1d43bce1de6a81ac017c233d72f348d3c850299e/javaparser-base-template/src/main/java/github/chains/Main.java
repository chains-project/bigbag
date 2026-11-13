package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        // The transformation will be applied to the source directory
        String sourceDirectory = "/workspace/nem";
        if (args.length > 0) {
            sourceDirectory = args[0];
        }

        try {
            SourceRoot sourceRoot = new SourceRoot(Paths.get(sourceDirectory));
            
            // Process all Java files
            sourceRoot.parse("", (localPath, absolutePath, compilationUnit) -> {
                if (compilationUnit.isPresent()) {
                    FlywayTransformer transformer = new FlywayTransformer();
                    transformer.visit(compilationUnit.get(), null);
                }
                return true;
            });
            
            // Save all modified files
            sourceRoot.saveAll();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Transformer that fixes Flyway API changes from older versions to 9.15.0+
     */
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        
        @Override
        public com.github.javaparser.ast.Node visit(ExpressionStmt n, Void arg) {
            // We want to transform statements like:
            // final org.flywaydb.core.Flyway flyway = new Flyway();
            // flyway.setDataSource(...);
            // flyway.setLocations(...);
            // flyway.setValidateOnMigrate(...);
            // Into:
            // final org.flywaydb.core.Flyway flyway = new Flyway(org.flywaydb.core.api.configuration.FluentConfiguration.configure()
            //     .dataSource(...)
            //     .locations(...)
            //     .validateOnMigrate(...));
            
            // Check if this is a variable declaration
            if (n.getExpression() instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr) {
                com.github.javaparser.ast.expr.VariableDeclarationExpr varDecl = 
                    (com.github.javaparser.ast.expr.VariableDeclarationExpr) n.getExpression();
                
                // Check if it's declaring a Flyway variable 
                if (varDecl.getVariables().size() > 0) {
                    VariableDeclarator var = varDecl.getVariables().get(0);
                    if (var.getType().asString().equals("org.flywaydb.core.Flyway")) {
                        // Check if it's initialized with new Flyway()
                        if (var.getInitializer().isPresent() && 
                            var.getInitializer().get() instanceof ObjectCreationExpr) {
                            
                            ObjectCreationExpr initExpr = (ObjectCreationExpr) var.getInitializer().get();
                            if (initExpr.getType().getNameAsString().equals("Flyway") && 
                                initExpr.getArguments().isEmpty()) {
                                
                                // Found the problematic pattern, replace with proper pattern
                                // Create: org.flywaydb.core.api.configuration.FluentConfiguration.configure()
                                MethodCallExpr fluentConfigure = new MethodCallExpr(
                                    new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                                    "configure"
                                );
                                
                                // Create: new Flyway(fluentConfigure)
                                ObjectCreationExpr newFlywayCreation = new ObjectCreationExpr(
                                    null,
                                    new ClassOrInterfaceType("org.flywaydb.core.Flyway"),
                                    NodeList.nodeList(fluentConfigure)
                                );
                                
                                // Set the new initializer
                                var.setInitializer(newFlywayCreation);
                            }
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}