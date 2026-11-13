package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project /path/to/transformed");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        String outputDirectory = args[1];

        System.out.println("Transforming source directory: " + sourceDirectory);
        System.out.println("Output directory: " + outputDirectory);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);

        try {
            launcher.run();

            int transformCount = 0;
            
            // Apply transformation: JAXBToStringStrategy.getInstance() -> JAXBToStringStrategy.INSTANCE
            for (CtInvocation<?> invocation : launcher.getFactory().getModel().getRootPackage().getElements(new TypeFilter<CtInvocation>(CtInvocation.class))) {
                // Check if this is a call to getInstance() method
                if (invocation.getExecutable() != null && 
                    "getInstance".equals(invocation.getExecutable().getSimpleName())) {
                    
                    // Check if the declaring type is JAXBToStringStrategy
                    if (invocation.getExecutable().getDeclaringType() != null) {
                        String declaringType = invocation.getExecutable().getDeclaringType().getQualifiedName();
                        if (declaringType.equals("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy") ||
                            declaringType.endsWith(".JAXBToStringStrategy")) {
                            
                            System.out.println("Found pattern at: " + invocation.getPosition());
                            transformCount++;
                            
                            Factory factory = invocation.getFactory();
                            
                            // Get the target expression (could be simple name or fully qualified)
                            String targetExpr = "";
                            if (invocation.getTarget() != null) {
                                targetExpr = invocation.getTarget().toString();
                            } else {
                                // If no explicit target, we need to use appropriate name
                                // Use simple name if it was used in the original code
                                targetExpr = "JAXBToStringStrategy";
                            }
                            
                            // Create the replacement: Target.INSTANCE
                            String replacement = targetExpr + ".INSTANCE";
                            
                            // Create a code snippet expression for the replacement
                            invocation.replace(factory.createCodeSnippetExpression(replacement));
                        }
                    }
                }
            }

            // Pretty-print the transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Transformed " + transformCount + " occurrences.");
            System.out.println("Transformed files written to: " + outputDirectory);

        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}