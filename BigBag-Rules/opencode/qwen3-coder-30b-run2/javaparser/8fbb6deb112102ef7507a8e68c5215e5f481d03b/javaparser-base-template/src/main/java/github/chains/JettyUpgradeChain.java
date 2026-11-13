package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JettyUpgradeChain {
    
    private final List<String> deprecatedClasses = Arrays.asList(
        "org.eclipse.jetty.server.SelectChannelConnector",
        "org.eclipse.jetty.server.nio.SelectChannelConnector"
    );
    
    private final List<String> replacementClasses = Arrays.asList(
        "org.eclipse.jetty.server.ServerConnector",
        "org.eclipse.jetty.server.ServerConnector"
    );
    
    public Optional<CompilationUnit> apply(CompilationUnit cu) {
        // Transform deprecated Jetty connector classes in type declarations
        transformTypeDeclarations(cu);
        
        // Transform deprecated Jetty connector classes in constructor calls
        transformConstructorCalls(cu);
        
        // Transform imports
        transformImports(cu);
        
        return Optional.of(cu);
    }
    
    private void transformTypeDeclarations(CompilationUnit cu) {
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            String qualifiedTypeName = type.asString();
            
            // Check if it's a deprecated connector class
            for (int i = 0; i < deprecatedClasses.size(); i++) {
                if (qualifiedTypeName.equals(deprecatedClasses.get(i))) {
                    // Replace with appropriate Jetty 11 connector
                    type.setName(replacementClasses.get(i));
                }
            }
        });
    }
    
    private void transformConstructorCalls(CompilationUnit cu) {
        cu.findAll(ObjectCreationExpr.class).forEach(constructorCall -> {
            if (constructorCall.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) constructorCall.getType();
                String qualifiedTypeName = type.asString();
                
                for (int i = 0; i < deprecatedClasses.size(); i++) {
                    if (qualifiedTypeName.equals(deprecatedClasses.get(i))) {
                        type.setName(replacementClasses.get(i));
                    }
                }
            }
        });
    }
    
    private void transformImports(CompilationUnit cu) {
        List<ImportDeclaration> importsToRemove = cu.getImports().stream()
            .filter(importDecl -> {
                String importName = importDecl.getNameAsString();
                return deprecatedClasses.contains(importName);
            })
            .collect(Collectors.toList());
        
        importsToRemove.forEach(cu::remove);
        
        // Add new imports for replacement classes
        for (int i = 0; i < replacementClasses.size(); i++) {
            String replacementClass = replacementClasses.get(i);
            if (!cu.getImports().stream().anyMatch(importDecl -> 
                importDecl.getNameAsString().equals(replacementClass))) {
                cu.addImport(replacementClass);
            }
        }
    }
}