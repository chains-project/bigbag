package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Reflections API breaking changes.
 * Specifically fixes calls to FilterBuilder.apply() which was removed in reflections 0.10.2
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Find all FilterBuilder.apply() invocations
        List<CtInvocation> filterBuilderApplyCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getExecutable() == null) return false;
                
                // Check if it's a call to FilterBuilder.apply()
                return element.getExecutable().getSimpleName().equals("apply") &&
                       element.getExecutable().getDeclaringType() != null &&
                       element.getExecutable().getDeclaringType().getQualifiedName().contains("FilterBuilder");
            }
        });
        
        // Replace FilterBuilder.apply() calls with correct API
        for (CtInvocation applyCall : filterBuilderApplyCalls) {
            // Get the FilterBuilder instance (target)
            CtExpression target = applyCall.getTarget();
            
            // Get the argument to apply()
            if (applyCall.getArguments().size() > 0) {
                CtExpression argument = applyCall.getArguments().get(0);
                
                // Create replacement: target.test(argument) instead of target.apply(argument)
                CtInvocation newCall = launcher.getFactory().createInvocation();
                newCall.setTarget(target);
                CtExecutableReference<?> testRef = launcher.getFactory().createExecutableReference();
                testRef.setSimpleName("test");
                testRef.setDeclaringType(launcher.getFactory().createReference("org.reflections.util.FilterBuilder"));
                newCall.setExecutable(testRef);
                newCall.setArguments(argument.clone());
                
                // Replace the old call with the new one
                applyCall.replace(newCall);
            }
        }
        
        System.out.println("Transformation completed. Check the transformed code in " + sourceDirectory + "-transformed");
        launcher.process();
    }
}