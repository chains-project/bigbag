package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformer.jar <sourceDir> <outputDir>");
            System.err.println("  sourceDir: Path to the source code directory to transform");
            System.err.println("  outputDir: Path where transformed code will be written");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        
        // Configure Spoon to handle missing dependencies
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath mode
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        
        // Add input source directory
        launcher.addInputResource(sourceDir);
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputDir);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Find all method invocations
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
            
            int transformationCount = 0;
            
            // Transform getArgumentAt to getArgument for Mockito InvocationOnMock
            for (CtInvocation<?> invocation : invocations) {
                if (invocation.getExecutable() != null && 
                    invocation.getExecutable().getSimpleName().equals("getArgumentAt")) {
                    
                    // Get the target type of the method call
                    CtExecutableReference<?> execRef = invocation.getExecutable();
                    CtTypeReference<?> declaringType = execRef.getDeclaringType();
                    
                    // Check if this is a method on org.mockito.invocation.InvocationOnMock
                    // In noClasspath mode, we can't always resolve types, so we use a more flexible approach
                    boolean isInvocationOnMock = false;
                    if (declaringType != null) {
                        String typeName = declaringType.getQualifiedName();
                        String simpleName = declaringType.getSimpleName();
                        
                        // Check for InvocationOnMock by name (exact match or ends with)
                        if ("org.mockito.invocation.InvocationOnMock".equals(typeName) ||
                            "InvocationOnMock".equals(simpleName) ||
                            (typeName != null && typeName.endsWith(".InvocationOnMock")) ||
                            "invocation.InvocationOnMock".equals(simpleName)) {
                            isInvocationOnMock = true;
                        }
                    }
                    
                    if (isInvocationOnMock) {
                        // Replace method name from getArgumentAt to getArgument
                        invocation.getExecutable().setSimpleName("getArgument");
                        transformationCount++;
                        
                        CtClass<?> parentClass = invocation.getParent(CtClass.class);
                        String className = parentClass != null ? parentClass.getQualifiedName() : "unknown";
                        System.out.println("Transformed getArgumentAt to getArgument in " + 
                                          className + " at line " + invocation.getPosition().getLine());
                    }
                }
            }
            
            System.out.println("Total transformations applied: " + transformationCount);
            
            if (transformationCount > 0) {
                // Write transformed code
                launcher.prettyprint();
                System.out.println("Transformation complete! Code written to: " + outputDir);
            } else {
                System.out.println("No transformations needed.");
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}