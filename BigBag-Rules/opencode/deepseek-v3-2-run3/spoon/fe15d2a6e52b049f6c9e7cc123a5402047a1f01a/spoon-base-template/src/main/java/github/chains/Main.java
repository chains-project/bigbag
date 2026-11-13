package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;
import java.util.Arrays;

public class Main {
    
    // List of JAXB strategy classes that changed from getInstance() method to INSTANCE field
    private static final List<String> JAXB_STRATEGY_CLASSES = Arrays.asList(
        "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy",
        "org.jvnet.jaxb2_commons.lang.JAXBCopyStrategy",
        "org.jvnet.jaxb2_commons.lang.JAXBEqualsStrategy",
        "org.jvnet.jaxb2_commons.lang.JAXBHashCodeStrategy",
        "org.jvnet.jaxb2_commons.lang.JAXBMergeStrategy"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying JAXB2-basics API fix transformation to: " + sourceDir);
        System.out.println("Fixing: getInstance() -> INSTANCE for JAXB*Strategy classes");
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            int totalTransformations = 0;
            
            // Process each JAXB strategy class
            for (String className : JAXB_STRATEGY_CLASSES) {
                int transformations = transformClassGetInstanceCalls(model, factory, className);
                totalTransformations += transformations;
                System.out.println("Fixed " + transformations + " occurrences of " + 
                                 getSimpleClassName(className) + ".getInstance()");
            }
            
            if (totalTransformations > 0) {
                launcher.setSourceOutputDirectory("./transformed-output");
                launcher.prettyprint();
                System.out.println("\nTotal transformations: " + totalTransformations);
                System.out.println("Transformation complete. Output written to ./transformed-output");
            } else {
                System.out.println("No JAXB strategy getInstance() calls found to transform.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformClassGetInstanceCalls(CtModel model, Factory factory, String className) {
        String simpleClassName = getSimpleClassName(className);
        int transformationCount = 0;
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                try {
                    if (invocation.getExecutable() == null || invocation.getExecutable().getSignature() == null) {
                        return false;
                    }
                    
                    String signature = invocation.getExecutable().getSignature();
                    if (!signature.equals("getInstance()")) {
                        return false;
                    }
                    
                    // Check if target type matches our class
                    if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
                        String targetType = invocation.getTarget().getType().getQualifiedName();
                        return targetType.equals(className);
                    }
                    
                    // Also check static method calls (no target)
                    if (invocation.getTarget() == null && invocation.getExecutable().getDeclaringType() != null) {
                        String declaringType = invocation.getExecutable().getDeclaringType().getQualifiedName();
                        return declaringType.equals(className);
                    }
                    
                } catch (Exception e) {
                    // Ignore parsing errors
                }
                return false;
            }
        });
        
        // Transform each invocation
        for (CtInvocation<?> invocation : invocations) {
            transformGetInstanceToInstanceField(invocation, factory, className);
            transformationCount++;
        }
        
        return transformationCount;
    }
    
    private static void transformGetInstanceToInstanceField(CtInvocation<?> invocation, Factory factory, String className) {
        // Create a field access expression: ClassName.INSTANCE
        String fieldAccessCode = className + ".INSTANCE";
        
        // Parse the field access code as an expression
        CtExpression<?> fieldAccessExpr = factory.Code().createCodeSnippetExpression(fieldAccessCode);
        
        // Replace the method invocation with field access
        invocation.replace(fieldAccessExpr);
    }
    
    private static String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}