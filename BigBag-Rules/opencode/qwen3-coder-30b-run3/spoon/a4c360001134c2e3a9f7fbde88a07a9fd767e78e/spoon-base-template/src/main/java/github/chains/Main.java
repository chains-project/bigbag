package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation to fix FilterBuilder.apply() -> FilterBuilder.test() calls
 * This fixes breaking changes in reflections 0.10.2 where FilterBuilder.apply() was removed
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.buildModel();

        // Find all invocations of apply() method on FilterBuilder objects
        List<CtInvocation<?>> applyInvocations = launcher.getFactory().getModel().getRootPackage()
                .filterChildren(new TypeFilter<>(CtInvocation.class))
                .list();

        int count = 0;
        for (CtInvocation<?> invocation : applyInvocations) {
            if (invocation.getExecutable() != null) {
                String methodName = invocation.getExecutable().getSimpleName();
                String declaringClassName = invocation.getExecutable().getDeclaringType().getQualifiedName();
                
                if ("apply".equals(methodName) && declaringClassName.contains("FilterBuilder")) {
                    // Replace the method name from "apply" to "test"
                    invocation.getExecutable().setSimpleName("test");
                    count++;
                }
            }
        }

        // Print transformation info
        System.out.println("Transformed " + count + " FilterBuilder.apply() calls");
        
        // Write the modified code back to the output directory
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
}