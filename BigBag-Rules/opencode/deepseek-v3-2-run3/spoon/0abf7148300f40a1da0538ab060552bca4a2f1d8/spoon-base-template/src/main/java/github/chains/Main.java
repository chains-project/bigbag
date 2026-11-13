package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking changes where method signatures
 * changed from accepting primitive types to wrapper types.
 * 
 * This specific implementation fixes the JasperReports 6.x breaking change:
 * - Old: JRPen.setLineWidth(float) or setLineWidth(int)
 * - New: JRPen.setLineWidth(Float)
 * 
 * The transformation is parameterized and can be adapted to similar breaking changes
 * by modifying the TARGET_METHOD and WRAPPER_CLASS constants.
 * 
 * IMPORTANT: This transformation works at the syntactic level and doesn't require
 * full classpath resolution. It matches method calls by name and wraps primitive
 * arguments with the specified wrapper class.
 */
public class Main {
    // Configuration: Change these for different breaking changes
    private static final String TARGET_METHOD = "setLineWidth";
    private static final String WRAPPER_CLASS = "java.lang.Float";
    private static final String WRAPPER_METHOD = "valueOf";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms " + TARGET_METHOD + " calls to use " + WRAPPER_CLASS + "." + WRAPPER_METHOD);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Looking for: " + TARGET_METHOD + " method calls");
        System.out.println("Wrapping primitive arguments with: " + WRAPPER_CLASS + "." + WRAPPER_METHOD);
        
        // Create launcher with NO_CLASSPATH to avoid dependency issues
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);  // Don't require dependencies
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF");  // Reduce logging
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find all method invocations with the target method name
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    CtExecutableReference<?> execRef = element.getExecutable();
                    if (execRef == null) return false;
                    
                    // Match method name only (simplified matching)
                    return TARGET_METHOD.equals(execRef.getSimpleName());
                }
            });
            
            System.out.println("Found " + invocations.size() + " " + TARGET_METHOD + " invocations");
            
            int transformed = 0;
            for (CtInvocation<?> invocation : invocations) {
                List<CtExpression<?>> arguments = invocation.getArguments();
                if (arguments.size() != 1) {
                    System.out.println("Warning: " + TARGET_METHOD + " call with " + arguments.size() + 
                                     " arguments at " + invocation.getPosition() + ", skipping");
                    continue;
                }
                
                CtExpression<?> arg = arguments.get(0);
                
                // Always try to wrap - if it's already wrapped, it won't hurt
                // (e.g., Float.valueOf(Float.valueOf(x)) is redundant but correct)
                try {
                    // Create wrapper class type access (e.g., Float.class)
                    CtTypeReference<?> wrapperTypeRef = launcher.getFactory().Type().createReference(WRAPPER_CLASS);
                    CtTypeAccess<?> wrapperTypeAccess = launcher.getFactory().createTypeAccess(wrapperTypeRef);
                    
                    // Create wrapper method invocation (e.g., Float.valueOf(arg))
                    CtInvocation<?> wrapperInvocation = launcher.getFactory().createInvocation(
                        wrapperTypeAccess,
                        launcher.getFactory().Executable().createReference(
                            wrapperTypeRef,
                            null,
                            WRAPPER_METHOD,
                            List.of(launcher.getFactory().Type().createReference(float.class))
                        ),
                        List.of(arg)
                    );
                    
                    // Replace the argument with wrapped version
                    invocation.setArguments(List.of(wrapperInvocation));
                    transformed++;
                    
                    System.out.println("Transformed " + TARGET_METHOD + " call at: " + 
                                     invocation.getPosition().toString());
                } catch (Exception e) {
                    System.out.println("Failed to transform " + TARGET_METHOD + " call at " + 
                                     invocation.getPosition() + ": " + e.getMessage());
                }
            }
            
            System.out.println("\nTransformation complete:");
            System.out.println("  Total invocations found: " + invocations.size());
            System.out.println("  Transformed: " + transformed);
            System.out.println("  Unchanged: " + (invocations.size() - transformed));
            
            // Apply the transformation to source files
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("\nTransformed files written back to: " + sourceDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}