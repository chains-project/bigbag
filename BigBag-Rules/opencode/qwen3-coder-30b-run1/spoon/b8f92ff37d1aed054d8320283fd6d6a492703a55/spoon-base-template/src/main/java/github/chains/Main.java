package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Maven Surefire 3.0.0-M7 breaking change
 * where TestListResolver.getWildcard() was removed.
 * 
 * This fixes all instances of:
 *   new DirectoryScanner(file, TestListResolver.getWildcard())
 * by replacing with:
 *   new DirectoryScanner(file, new DefaultTestListResolver())
 * 
 * This is a generic fix that works for any project using Maven Surefire with this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.buildModel();
        
        // Find all DirectoryScanner constructor calls 
        List<CtConstructorCall> directoryScannerCalls = launcher.getModel().getRootPackage().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        ).stream()
            .filter(call -> call.getExecutable() != null && 
                           call.getExecutable().getDeclaringType() != null &&
                           call.getExecutable().getDeclaringType().getSimpleName().equals("DirectoryScanner"))
            .toList();
        
        int count = 0;
        
        for (CtConstructorCall directoryScannerCall : directoryScannerCalls) {
            // Check if it has 2 arguments and the second one contains getWildcard
            if (directoryScannerCall.getArguments().size() >= 2) {
                String arg2Str = directoryScannerCall.getArguments().get(1).toString();
                if (arg2Str.contains("TestListResolver.getWildcard")) {
                    // Replace the problematic second argument with new DefaultTestListResolver()
                    CtNewClass newResolver = launcher.getFactory().Core().createNewClass();
                    newResolver.setExecutable(
                        launcher.getFactory().Executable().createReference(
                            "org.apache.maven.surefire.api.testset.DefaultTestListResolver",
                            "DefaultTestListResolver"
                        )
                    );
                    
                    // We need to create a proper replacement - let's do this by string replacement
                    directoryScannerCall.getArguments().set(1, newResolver);
                    count++;
                }
            }
        }
        
        if (count > 0) {
            System.out.println("Successfully updated " + count + " instances of TestListResolver.getWildcard()");
            System.out.println("The code should now compile with Maven Surefire 3.0.0-M7+");
        } else {
            System.out.println("No instances of TestListResolver.getWildcard() found.");
        }
        
        // Print the fixed code to stdout for verification
        System.out.println("\nFixed code preview:");
        launcher.getModel().getRootPackage().accept(new spoon.reflect.visitor.DefaultJavaPrettyPrinter(launcher.getFactory().getEnvironment()));
    }
}