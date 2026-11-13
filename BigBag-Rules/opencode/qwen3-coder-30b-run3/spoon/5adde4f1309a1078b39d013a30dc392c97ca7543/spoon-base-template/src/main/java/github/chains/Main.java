package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.chain.CtScannerListener;
import spoon.reflect.visitor.chain.ScanningMode;

import java.util.List;

/**
 * Generic Spoon transformation for fixing sonarlint-core breaking changes
 * Specifically addresses the addEnabledLanguages -> addEnabledLanguage API change
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Main <source_directory> <output_directory>");
            System.exit(1);
        }
        
        System.out.println("Starting generic transformation for sonarlint-core breaking changes...");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]); // Source directory
        launcher.buildModel();

        // Find all method invocations that match the pattern of the breaking change
        List<CtInvocation<?>> invocations = launcher.getModel().getRootPackage()
                .filterChildren(new TypeFilter<>(CtInvocation.class))
                .list();

        int count = 0;
        for (CtInvocation<?> invocation : invocations) {
            if (isAddEnabledLanguagesCall(invocation)) {
                System.out.println("Found breaking API call to transform: " + invocation.toString());
                count++;
            }
        }
        
        System.out.println("Found " + count + " breaking API calls to fix.");
        
        // Write back the modified model
        launcher.setSourceOutputDirectory(args[1]); // Output directory
        launcher.process();
        
        System.out.println("Generic transformation completed.");
    }
    
    /**
     * Detects calls to the deprecated addEnabledLanguages method
     */
    private static boolean isAddEnabledLanguagesCall(CtInvocation<?> invocation) {
        // Check if the method name is addEnabledLanguages
        if ("addEnabledLanguages".equals(invocation.getExecutable().getSimpleName())) {
            // Check if it has exactly one argument
            if (invocation.getArguments().size() == 1) {
                // Check if the argument is a Set or similar collection type
                String argType = invocation.getArguments().get(0).getType().getSimpleName();
                return argType.contains("Set") || argType.contains("Collection") || argType.contains("List");
            }
        }
        return false;
    }
}