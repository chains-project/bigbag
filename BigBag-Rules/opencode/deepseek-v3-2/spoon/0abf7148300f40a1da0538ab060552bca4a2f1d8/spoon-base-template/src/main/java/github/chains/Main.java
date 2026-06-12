package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying JasperReports 6.19.1 migration transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8); // Java 8
        launcher.addInputResource(sourceDir);
        
        // Build the model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = Query.getElements(
            launcher.getFactory(), 
            new TypeFilter<CtInvocation<?>>(CtInvocation.class)
        );
        
        int transformationCount = 0;
        
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            String methodName = execRef.getSimpleName();
            
            // Check if this is setLineWidth method call (main breaking change in this project)
            if ("setLineWidth".equals(methodName)) {
                // Check if the method has exactly one parameter
                if (invocation.getArguments().size() == 1) {
                    CtExpression<?> arg = invocation.getArguments().get(0);
                    
                    // Create Float.valueOf() wrapper
                    CtTypeAccess<Float> floatTypeAccess = factory.createTypeAccess(
                        factory.Type().createReference(Float.class)
                    );
                    
                    // Create method reference for Float.valueOf(float)
                    CtExecutableReference<Float> valueOfRef = factory.Core().createExecutableReference();
                    valueOfRef.setSimpleName("valueOf");
                    valueOfRef.setDeclaringType(factory.Type().createReference(Float.class));
                    valueOfRef.setType(factory.Type().createReference(Float.class));
                    
                    // Create the invocation: Float.valueOf(arg)
                    CtInvocation<Float> valueOfInvocation = factory.createInvocation(
                        floatTypeAccess,
                        valueOfRef,
                        arg
                    );
                    
                    // Replace the argument with Float.valueOf(arg)
                    invocation.getArguments().set(0, valueOfInvocation);
                    
                    System.out.println("Transformed setLineWidth call at: " + invocation.getPosition());
                    transformationCount++;
                }
            }
        }
        
        System.out.println("Applied " + transformationCount + " transformations for JasperReports 6.19.1 migration");
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
}