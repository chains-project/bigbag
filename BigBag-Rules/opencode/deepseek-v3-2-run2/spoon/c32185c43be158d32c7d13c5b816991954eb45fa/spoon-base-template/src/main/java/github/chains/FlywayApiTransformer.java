package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation rule for Flyway API breaking change.
 * 
 * Transforms:
 *   Old: new Flyway() + setters (setDataSource, setClassLoader, setLocations, setValidateOnMigrate)
 *   New: Flyway.configure().dataSource(...).load()
 *   
 * This is a generic rule applicable to ANY Java project affected by Flyway 9.x API changes.
 */
public class FlywayApiTransformer {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java FlywayApiTransformer <source-dir> <output-dir>");
            System.err.println("Example: java FlywayApiTransformer /path/to/project /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.addProcessor(new FlywayApiTransformationProcessor());
        
        launcher.run();
        System.out.println("Flyway API transformation completed successfully!");
        System.out.println("Output written to: " + outputDir);
    }
    
    static class FlywayApiTransformationProcessor extends spoon.processing.AbstractProcessor<CtNewClass<?>> {
        private static final List<String> SETTER_METHODS = Arrays.asList(
            "setDataSource", "setClassLoader", "setLocations", "setValidateOnMigrate"
        );
        
        @Override
        public void process(CtNewClass<?> newClass) {
            // Check if this is a Flyway constructor call
            CtTypeReference<?> typeRef = newClass.getType();
            if (typeRef == null || !"org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName())) {
                return;
            }
            
            // Check if it's a no-arg constructor
            CtExecutableReference<?> constructorRef = newClass.getExecutable();
            if (constructorRef == null || constructorRef.getParameters().size() != 0) {
                return;
            }
            
            // Find the block containing this constructor
            CtBlock<?> parentBlock = newClass.getParent(CtBlock.class);
            if (parentBlock == null) {
                return;
            }
            
            // Find setter calls that follow this constructor
            List<CtStatement> statements = parentBlock.getStatements();
            int constructorIndex = statements.indexOf(newClass.getParent(CtStatement.class));
            if (constructorIndex == -1) {
                return;
            }
            
            // Collect setter calls in sequence after the constructor
            List<CtInvocation<?>> setterCalls = new ArrayList<>();
            for (int i = constructorIndex + 1; i < statements.size(); i++) {
                CtStatement stmt = statements.get(i);
                CtInvocation<?> invocation = extractInvocation(stmt);
                if (invocation != null && SETTER_METHODS.contains(invocation.getExecutable().getSimpleName())) {
                    setterCalls.add(invocation);
                } else {
                    break;
                }
            }
            
            if (setterCalls.isEmpty()) {
                return;
            }
            
            // Transform the code
            transformCode(newClass, setterCalls);
        }
        
        private CtInvocation<?> extractInvocation(CtStatement stmt) {
            if (stmt instanceof CtInvocation) {
                return (CtInvocation<?>) stmt;
            } else if (stmt instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) stmt;
                if (assignment.getAssignment() instanceof CtInvocation) {
                    return (CtInvocation<?>) assignment.getAssignment();
                }
            }
            return null;
        }
        
        private void transformCode(CtNewClass<?> newClass, List<CtInvocation<?>> setterCalls) {
            Factory factory = getFactory();
            
            // Build the fluent API chain
            StringBuilder fluentChain = new StringBuilder("org.flywaydb.core.Flyway.configure()");
            
            for (CtInvocation<?> setter : setterCalls) {
                String setterName = setter.getExecutable().getSimpleName();
                String fluentMethod = mapToFluentMethod(setterName);
                
                if (fluentMethod != null && !setter.getArguments().isEmpty()) {
                    fluentChain.append("\n\t.");
                    fluentChain.append(fluentMethod);
                    fluentChain.append("(");
                    fluentChain.append(setter.getArguments().get(0).toString());
                    fluentChain.append(")");
                }
            }
            
            fluentChain.append("\n\t.load()");
            
            // Get the variable that holds the Flyway instance
            CtElement parent = newClass.getParent();
            while (parent != null && !(parent instanceof CtVariable)) {
                parent = parent.getParent();
            }
            
            // Replace with return statement if in a flyway() method
            CtMethod<?> parentMethod = newClass.getParent(CtMethod.class);
            if (parentMethod != null && parentMethod.getSimpleName().toLowerCase().contains("flyway")) {
                // Create return statement
                CtReturn<?> returnStmt = factory.createReturn();
                returnStmt.setReturnedExpression(factory.createCodeSnippetExpression(fluentChain.toString()));
                
                // Clear method body and add return
                CtBlock<?> methodBody = parentMethod.getBody();
                if (methodBody != null) {
                    methodBody.getStatements().clear();
                    methodBody.addStatement(returnStmt);
                }
            } else if (parent instanceof CtVariable) {
                // Replace variable initialization
                CtVariable<?> variable = (CtVariable<?>) parent;
                variable.setDefaultExpression(factory.createCodeSnippetExpression(fluentChain.toString()));
            }
            
            // Remove the setter calls
            for (CtInvocation<?> setter : setterCalls) {
                setter.delete();
            }
        }
        
        private String mapToFluentMethod(String setterName) {
            switch (setterName) {
                case "setDataSource": return "dataSource";
                case "setClassLoader": return "classLoader";
                case "setLocations": return "locations";
                case "setValidateOnMigrate": return "validateOnMigrate";
                default: return null;
            }
        }
    }
}