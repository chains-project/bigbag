package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;

import java.io.File;

/**
 * Generic Spoon transformation to fix Hamcrest string constructor breaking changes.
 * 
 * Breaking Change Pattern:
 * - Old API: StringContains(String substring), StringStartsWith(String substring)  
 * - New API: StringContains(boolean ignoreCase, String substring), StringStartsWith(boolean ignoreCase, String substring)
 * - Transformation: new StringContains(boolean, substring) → new StringContains(substring)
 * - Transformation: new StringStartsWith(boolean, substring) → new StringStartsWith(substring)
 * 
 * This transformation is generic and can be applied to any project affected by
 * the Hamcrest StringContains and StringStartsWith constructor signature changes.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source-directory>");
            System.err.println("Example: java -jar spoon-base.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying StringContains transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        launcher.buildModel();
        
        // Apply transformation
        transformStringContainsCalls(launcher);
        
        // Generate transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Transforms all StringContains and StringStartsWith constructor calls from two-parameter to single-parameter form.
     * This fixes the breaking change in Hamcrest where the constructor signature changed.
     */
    private static void transformStringContainsCalls(Launcher launcher) {
        // Find all constructor calls to StringContains and StringStartsWith
        launcher.getModel()
            .getElements(new TypeFilter<>(CtConstructorCall.class))
            .stream()
            .filter(Main::isHamcrestStringTwoParamCall)
            .forEach(Main::transformHamcrestStringCall);
    }
    
    /**
     * Checks if a constructor call matches the pattern: new StringContains(boolean, String) or new StringStartsWith(boolean, String)
     */
    private static boolean isHamcrestStringTwoParamCall(CtConstructorCall<?> call) {
        return call.getType() != null &&
               ("org.hamcrest.core.StringContains".equals(call.getType().getQualifiedName()) ||
                "org.hamcrest.core.StringStartsWith".equals(call.getType().getQualifiedName())) &&
               call.getArguments().size() == 2 &&
               call.getArguments().get(0).getType() != null &&
               "boolean".equals(call.getArguments().get(0).getType().getQualifiedName()) &&
               call.getArguments().get(1).getType() != null &&
               "java.lang.String".equals(call.getArguments().get(1).getType().getQualifiedName());
    }
    
    /**
     * Transforms a single Hamcrest string constructor call from:
     * new StringContains(boolean, substring) → new StringContains(substring)
     * new StringStartsWith(boolean, substring) → new StringStartsWith(substring)
     */
    private static void transformHamcrestStringCall(CtConstructorCall<?> call) {
        String className = call.getType().getQualifiedName();
        System.out.println("Transforming " + className + " call at: " + call.getPosition());
        
        // Get the existing substring argument (second parameter)
        var substringArg = call.getArguments().get(1);
        
        // Clear existing arguments
        call.getArguments().clear();
        
        // Add only the substring argument
        call.addArgument(substringArg);
    }
}