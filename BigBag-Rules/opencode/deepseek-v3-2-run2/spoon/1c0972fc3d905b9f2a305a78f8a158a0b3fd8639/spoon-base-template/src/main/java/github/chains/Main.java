package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        int changesMade = 0;
        
        // Pattern 1: Remove imports of Maven31DependencyGraphBuilder
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            String importStr = ctImport.toString();
            if (importStr.contains("Maven31DependencyGraphBuilder")) {
                System.out.println("Removing import: " + importStr);
                ctImport.delete();
                changesMade++;
            }
        }
        
        // Pattern 2: Replace constructor calls to Maven31DependencyGraphBuilder
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                if (constructorCall.getType() == null) return false;
                String typeName = constructorCall.getType().getQualifiedName();
                return typeName != null && typeName.contains("Maven31DependencyGraphBuilder");
            }
        });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("Found constructor call: " + constructorCall);
            
            // Check the context to decide what to replace it with
            String context = constructorCall.getParent() != null ? constructorCall.getParent().toString() : "";
            
            if (context.contains("Optional.ofNullable") && context.contains(".orElse(")) {
                // Pattern: Optional.ofNullable(graph).orElse(new Maven31DependencyGraphBuilder())
                // Replace with null - this removes the fallback default
                CtLiteral<?> nullLiteral = factory.createLiteral(null);
                constructorCall.replace(nullLiteral);
                System.out.println("  -> Replaced with null (removing fallback default)");
            } else {
                // Other context - replace with RuntimeException that explains the issue
                CtCodeSnippetExpression<?> runtimeException = factory.createCodeSnippetExpression(
                    "new RuntimeException(\"Maven31DependencyGraphBuilder was removed in maven-dependency-tree 3.2.1. " +
                    "Please use dependency injection to obtain a DependencyGraphBuilder or provide a non-null instance.\")"
                );
                constructorCall.replace(runtimeException);
                System.out.println("  -> Replaced with RuntimeException explaining the API change");
            }
            changesMade++;
        }
        
        if (changesMade > 0) {
            // Output transformed code
            String outputDir = sourceDir + "-transformed";
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("\n" + changesMade + " changes made!");
            System.out.println("Transformed code written to: " + outputDir);
            System.out.println("\nIMPORTANT: This transformation fixes compilation by removing references to the removed class.");
            System.out.println("You may need to manually update your code to:");
            System.out.println("1. Use dependency injection to get DependencyGraphBuilder instances");
            System.out.println("2. Or ensure DependencyGraphBuilder parameters are never null");
            System.out.println("3. Or update to use DefaultDependencyGraphBuilder with proper ProjectDependenciesResolver");
        } else {
            System.out.println("No Maven31DependencyGraphBuilder references found.");
        }
    }
}