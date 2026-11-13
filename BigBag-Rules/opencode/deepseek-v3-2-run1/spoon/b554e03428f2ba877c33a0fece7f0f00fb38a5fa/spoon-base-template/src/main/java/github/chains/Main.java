package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import spoon.compiler.SpoonResource;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtImport;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find and fix Maven31DependencyGraphBuilder references
            int fixesApplied = 0;
            
            // Find all constructor calls to Maven31DependencyGraphBuilder
            List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getExecutable() != null && constructorCall.getExecutable().getDeclaringType() != null) {
                        String typeName = constructorCall.getExecutable().getDeclaringType().getQualifiedName();
                        return "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder".equals(typeName);
                    }
                    return false;
                }
            });
            
            // Fix each constructor call
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                System.out.println("Found Maven31DependencyGraphBuilder constructor call at: " + constructorCall.getPosition());
                
                // Replace with null and add a comment
                // We replace new Maven31DependencyGraphBuilder() with null because:
                // 1. Maven31DependencyGraphBuilder was removed in maven-dependency-tree 3.2.0
                // 2. DefaultDependencyGraphBuilder requires a ProjectDependenciesResolver parameter
                // 3. In practice, DependencyGraphBuilder should be injected via @Component
                CtCodeSnippetExpression<?> nullExpr = factory.createCodeSnippetExpression("null");
                constructorCall.replace(nullExpr);
                fixesApplied++;
            }
            
            // Find and update imports for Maven31DependencyGraphBuilder
            List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class) {
                @Override
                public boolean matches(CtImport importDecl) {
                    if (importDecl.getReference() instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) importDecl.getReference();
                        return "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder".equals(typeRef.getQualifiedName());
                    }
                    return false;
                }
            });
            
            for (CtImport importDecl : imports) {
                System.out.println("Found Maven31DependencyGraphBuilder import at: " + importDecl.getPosition());
                
                // Create new import for DefaultDependencyGraphBuilder
                CtTypeReference<?> newTypeRef = factory.Type().createReference(
                    "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder"
                );
                
                // Replace the import
                try {
                    // Create a new import statement
                    CtImport newImport = factory.createImport(newTypeRef);
                    importDecl.replace(newImport);
                    System.out.println("Replaced import with DefaultDependencyGraphBuilder");
                    fixesApplied++;
                } catch (Exception e) {
                    System.err.println("Error updating import: " + e.getMessage());
                }
            }
            
            // Also find type references (e.g., in variable declarations, method parameters, etc.)
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder".equals(typeRef.getQualifiedName());
                }
            });
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                System.out.println("Found Maven31DependencyGraphBuilder type reference at: " + typeRef.getPosition());
                
                // Replace with DefaultDependencyGraphBuilder
                CtTypeReference<?> newTypeRef = factory.Type().createReference(
                    "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder"
                );
                typeRef.replace(newTypeRef);
                fixesApplied++;
            }
            
            System.out.println("Applied " + fixesApplied + " fixes");
            
            // Output transformed code
            launcher.setSourceOutputDirectory(sourceDir + "-transformed");
            launcher.prettyprint();
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}