package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation rule for maven-surefire-common API breaking change:
 * Replaces TestListResolver.getWildcard() static method calls with constructor calls.
 * 
 * The transformation is generic and can be applied to any Java project affected by
 * the removal of TestListResolver.getWildcard() method in maven-surefire-common 3.0.0-M7.
 * 
 * Transformation pattern:
 * Old: TestListResolver.getWildcard()
 * New: new TestListResolver((String) null)  // Assumes null parameter creates wildcard resolver
 * 
 * Usage: java -jar transformation.jar <source-directory>
 */
public class Main {
    // Configuration: Old and new API patterns
    private static final String OLD_CLASS = "org.apache.maven.surefire.api.testset.TestListResolver";
    private static final String OLD_METHOD = "getWildcard";
    private static final String NEW_CONSTRUCTOR_PARAM = null; // null for wildcard
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Transforms: " + OLD_CLASS + "." + OLD_METHOD + "() -> new " + OLD_CLASS + "((String) null)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_CLASS + "." + OLD_METHOD + "() -> new " + OLD_CLASS + "((String) null)");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations matching the pattern
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null || element.getExecutable().getSignature() == null) {
                    return false;
                }
                
                String signature = element.getExecutable().getSignature();
                String target = element.getTarget() != null ? element.getTarget().toString() : "";
                
                // Match exact pattern: TestListResolver.getWildcard()
                return signature.contains(OLD_METHOD + "()") && 
                       (target.contains(OLD_CLASS.substring(OLD_CLASS.lastIndexOf('.') + 1)) || 
                        signature.contains(OLD_CLASS + "." + OLD_METHOD));
            }
        });
        
        System.out.println("Found " + invocations.size() + " occurrences of " + OLD_CLASS + "." + OLD_METHOD + "()");
        
        // Transform each invocation
        int transformed = 0;
        for (CtInvocation<?> invocation : invocations) {
            try {
                System.out.println("Transforming invocation at: " + invocation.getPosition());
                
                // Create constructor call
                CtConstructorCall<?> constructorCall = launcher.getFactory().createConstructorCall(
                    launcher.getFactory().Type().createReference(OLD_CLASS)
                );
                
                // Add parameter (null for wildcard)
                if (NEW_CONSTRUCTOR_PARAM != null) {
                    CtLiteral<String> paramLiteral = launcher.getFactory().createLiteral(NEW_CONSTRUCTOR_PARAM);
                    paramLiteral.setType(launcher.getFactory().Type().STRING);
                    constructorCall.addArgument(paramLiteral);
                } else {
                    // null literal for wildcard
                    CtLiteral<String> nullLiteral = launcher.getFactory().createLiteral(null);
                    nullLiteral.setType(launcher.getFactory().Type().STRING);
                    constructorCall.addArgument(nullLiteral);
                }
                
                // Replace the method invocation with constructor call
                invocation.replace(constructorCall);
                transformed++;
            } catch (Exception e) {
                System.err.println("Error transforming invocation at " + invocation.getPosition() + ": " + e.getMessage());
            }
        }
        
        // Apply the transformation by pretty-printing
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed. " + transformed + "/" + invocations.size() + " occurrences transformed.");
        System.out.println("Transformed files written back to: " + sourceDir);
        
        if (transformed > 0) {
            System.out.println("\nNOTE: This transformation assumes the new API accepts null parameter for wildcard.");
            System.out.println("If this assumption is incorrect, modify NEW_CONSTRUCTOR_PARAM constant.");
        }
    }
}