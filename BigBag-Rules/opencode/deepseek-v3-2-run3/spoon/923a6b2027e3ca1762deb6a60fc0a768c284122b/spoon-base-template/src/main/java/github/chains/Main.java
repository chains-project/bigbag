package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;
import java.io.File;

public class Main {
    
    /**
     * Generic transformation rule for hamcrest breaking changes from 1.x to 2.x.
     * Handles three common breaking changes:
     * 1. emptyIterableOf(Class) → emptyIterable()
     * 2. describedAs(String, Matcher) → describedAs(String, Matcher, Object...)
     * 3. MatcherAssert.assertThat(T, Matcher) → MatcherAssert.assertThat(String, T, Matcher)
     * 
     * All transformations are parameterized by fully-qualified type names.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying hamcrest breaking change transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        int transformations = 0;
        
        // Transform emptyIterableOf calls
        transformations += model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class))
            .stream()
            .filter(invocation -> isEmptyIterableOfInvocation(invocation))
            .map(invocation -> transformEmptyIterableOf(invocation, launcher))
            .filter(result -> result)
            .count();
        
        // Transform describedAs calls (2 parameters → 3 parameters)
        transformations += model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class))
            .stream()
            .filter(invocation -> isDescribedAsTwoParamInvocation(invocation))
            .map(invocation -> transformDescribedAs(invocation, launcher))
            .filter(result -> result)
            .count();
        
        // Transform MatcherAssert.assertThat calls (2 parameters → 3 parameters)
        transformations += model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class))
            .stream()
            .filter(invocation -> isMatcherAssertTwoParamInvocation(invocation))
            .map(invocation -> transformMatcherAssert(invocation, launcher))
            .filter(result -> result)
            .count();
        
        // Apply the transformation
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Applied " + transformations + " transformations.");
    }
    
    /**
     * Check if the invocation is a call to Matchers.emptyIterableOf
     */
    private static boolean isEmptyIterableOfInvocation(CtInvocation<?> invocation) {
        String targetSignature = invocation.getExecutable().getSignature();
        String targetType = invocation.getExecutable().getDeclaringType() != null 
            ? invocation.getExecutable().getDeclaringType().getQualifiedName() 
            : "";
        
        // Match calls to Matchers.emptyIterableOf with any parameter type
        return targetType.equals("org.hamcrest.Matchers") && 
               targetSignature.startsWith("emptyIterableOf(");
    }
    
    /**
     * Check if the invocation is a call to Matchers.describedAs with 2 parameters
     */
    private static boolean isDescribedAsTwoParamInvocation(CtInvocation<?> invocation) {
        String targetSignature = invocation.getExecutable().getSignature();
        String targetType = invocation.getExecutable().getDeclaringType() != null 
            ? invocation.getExecutable().getDeclaringType().getQualifiedName() 
            : "";
        
        // Match calls to Matchers.describedAs with exactly 2 parameters
        return targetType.equals("org.hamcrest.Matchers") && 
               targetSignature.startsWith("describedAs(") &&
               invocation.getArguments().size() == 2;
    }
    
    /**
     * Check if the invocation is a call to MatcherAssert.assertThat with 2 parameters
     */
    private static boolean isMatcherAssertTwoParamInvocation(CtInvocation<?> invocation) {
        String targetSignature = invocation.getExecutable().getSignature();
        String targetType = invocation.getExecutable().getDeclaringType() != null 
            ? invocation.getExecutable().getDeclaringType().getQualifiedName() 
            : "";
        
        // Match calls to MatcherAssert.assertThat with exactly 2 parameters
        return targetType.equals("org.hamcrest.MatcherAssert") && 
               targetSignature.startsWith("assertThat(") &&
               invocation.getArguments().size() == 2;
    }
    
    /**
     * Transform emptyIterableOf call to emptyIterable()
     */
    private static boolean transformEmptyIterableOf(CtInvocation<?> invocation, Launcher launcher) {
        try {
            // Create a new invocation for Matchers.emptyIterable()
            CtInvocation<?> newInvocation = launcher.getFactory().createInvocation(
                launcher.getFactory().createTypeAccess(
                    launcher.getFactory().createReference("org.hamcrest.Matchers")
                ),
                launcher.getFactory().createExecutableReference()
                    .setDeclaringType(launcher.getFactory().createReference("org.hamcrest.Matchers"))
                    .setSimpleName("emptyIterable")
            );
            
            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
            System.out.println("Transformed: " + invocation.getPosition() + 
                             " - Replaced emptyIterableOf with emptyIterable");
            return true;
        } catch (Exception e) {
            System.err.println("Error transforming invocation at " + invocation.getPosition() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Transform describedAs(String, Matcher) to describedAs(String, Matcher, Object...)
     * Adds empty varargs parameter
     */
    private static boolean transformDescribedAs(CtInvocation<?> invocation, Launcher launcher) {
        try {
            // For describedAs, we'll take a simpler approach - just add an empty array literal
            // since the varargs parameter is optional and can be empty
            java.util.List<spoon.reflect.code.CtExpression<?>> args = new java.util.ArrayList<>();
            args.addAll(invocation.getArguments());
            
            // Create an empty array literal for varargs
            spoon.reflect.code.CtNewArray<?> emptyArray = launcher.getFactory().createNewArray();
            emptyArray.setType(launcher.getFactory().createReference("java.lang.Object[]"));
            args.add(emptyArray);
            
            // Create new invocation with 3 parameters
            CtInvocation<?> newInvocation = launcher.getFactory().createInvocation(
                launcher.getFactory().createTypeAccess(
                    launcher.getFactory().createReference("org.hamcrest.Matchers")
                ),
                launcher.getFactory().createExecutableReference()
                    .setDeclaringType(launcher.getFactory().createReference("org.hamcrest.Matchers"))
                    .setSimpleName("describedAs"),
                args
            );
            
            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
            System.out.println("Transformed: " + invocation.getPosition() + 
                             " - Updated describedAs from 2 to 3 parameters");
            return true;
        } catch (Exception e) {
            System.err.println("Error transforming describedAs at " + invocation.getPosition() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Transform MatcherAssert.assertThat(T, Matcher) to MatcherAssert.assertThat(String, T, Matcher)
     * Adds empty string as first parameter
     */
    private static boolean transformMatcherAssert(CtInvocation<?> invocation, Launcher launcher) {
        try {
            // Get original arguments
            java.util.List<spoon.reflect.code.CtExpression<?>> args = new java.util.ArrayList<>();
            
            // Add empty string as first parameter (reason)
            args.add(launcher.getFactory().createLiteral(""));
            
            // Add original arguments
            args.addAll(invocation.getArguments());
            
            // Create new invocation with 3 parameters
            CtInvocation<?> newInvocation = launcher.getFactory().createInvocation(
                launcher.getFactory().createTypeAccess(
                    launcher.getFactory().createReference("org.hamcrest.MatcherAssert")
                ),
                launcher.getFactory().createExecutableReference()
                    .setDeclaringType(launcher.getFactory().createReference("org.hamcrest.MatcherAssert"))
                    .setSimpleName("assertThat"),
                args
            );
            
            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
            System.out.println("Transformed: " + invocation.getPosition() + 
                             " - Updated MatcherAssert.assertThat from 2 to 3 parameters");
            return true;
        } catch (Exception e) {
            System.err.println("Error transforming MatcherAssert at " + invocation.getPosition() + ": " + e.getMessage());
            return false;
        }
    }
}