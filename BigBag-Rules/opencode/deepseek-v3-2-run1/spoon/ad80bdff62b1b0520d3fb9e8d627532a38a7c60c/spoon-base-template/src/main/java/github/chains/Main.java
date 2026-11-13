package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find all Flyway constructor calls
            List<CtConstructorCall> flywayCtors = launcher.getModel()
                .getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall element) {
                        CtTypeReference typeRef = element.getType();
                        return typeRef != null && 
                               "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName()) &&
                               element.getArguments().isEmpty();
                    }
                });
            
            System.out.println("Found " + flywayCtors.size() + " Flyway constructor calls");
            
            for (CtConstructorCall ctorCall : flywayCtors) {
                System.out.println("Processing Flyway constructor at: " + ctorCall.getPosition());
                
                // Get the parent method to find all setter calls
                CtMethod parentMethod = ctorCall.getParent(CtMethod.class);
                if (parentMethod == null) {
                    System.out.println("  Warning: Could not find parent method for constructor at line " + ctorCall.getPosition().getLine());
                    continue;
                }
                
                // Find the variable name that holds the Flyway instance
                String varName = null;
                CtLocalVariable localVar = ctorCall.getParent(CtLocalVariable.class);
                if (localVar != null) {
                    varName = localVar.getSimpleName();
                } else {
                    // Try to find assignment
                    varName = "flyway"; // Default name
                }
                final String finalVarName = varName;
                
                // Build the transformation string
                StringBuilder transformation = new StringBuilder();
                transformation.append("Flyway.configure()");
                
                // Find all setter calls on this variable in the same method
                List<CtInvocation> invocations = parentMethod.getElements(
                    new TypeFilter<CtInvocation>(CtInvocation.class) {
                        @Override
                        public boolean matches(CtInvocation element) {
                            CtExpression target = element.getTarget();
                            return target != null && 
                                   target.toString().equals(finalVarName) &&
                                   element.getExecutable().getSimpleName().startsWith("set");
                        }
                    }
                );
                
                // Sort invocations by position to maintain order
                invocations.sort((a, b) -> a.getPosition().getLine() - b.getPosition().getLine());
                
                // Map setter names to builder method names
                for (CtInvocation setterCall : invocations) {
                    String setterName = setterCall.getExecutable().getSimpleName();
                    String builderMethodName = mapSetterToBuilder(setterName);
                    
                    if (builderMethodName != null) {
                        transformation.append(".").append(builderMethodName).append("(");
                        List<CtExpression> setterArgs = setterCall.getArguments();
                        for (int i = 0; i < setterArgs.size(); i++) {
                            if (i > 0) transformation.append(", ");
                            transformation.append(setterArgs.get(i).toString());
                        }
                        transformation.append(")");
                        
                        // Delete the old setter call
                        setterCall.delete();
                    }
                }
                
                transformation.append(".load()");
                
                // Replace the constructor call with the new builder pattern
                String replacementCode = transformation.toString();
                CtExpression newExpr = factory.createCodeSnippetExpression(replacementCode);
                ctorCall.replace(newExpr);
                
                System.out.println("  Transformed constructor at line " + ctorCall.getPosition().getLine());
                System.out.println("  Replacement: " + replacementCode);
            }
            
            // Output the transformed code
            System.out.println("\nWriting transformed files...");
            String outputDir = "/tmp/nem-transformed";
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete. Output in: " + outputDir);
            
            // Copy transformed files back to original location for testing
            System.out.println("Copying transformed files back to original location...");
            ProcessBuilder pb = new ProcessBuilder("cp", "-r", outputDir + "/.", sourceDir);
            Process p = pb.start();
            p.waitFor();
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String mapSetterToBuilder(String setterName) {
        switch (setterName) {
            case "setDataSource": return "dataSource";
            case "setLocations": return "locations";
            case "setValidateOnMigrate": return "validateOnMigrate";
            case "setBaselineOnMigrate": return "baselineOnMigrate";
            case "setBaselineVersion": return "baselineVersion";
            case "setBaselineDescription": return "baselineDescription";
            case "setEncoding": return "encoding";
            case "setTable": return "table";
            case "setTarget": return "target";
            case "setSchemas": return "schemas";
            case "setPlaceholders": return "placeholders";
            default: return null;
        }
    }
}