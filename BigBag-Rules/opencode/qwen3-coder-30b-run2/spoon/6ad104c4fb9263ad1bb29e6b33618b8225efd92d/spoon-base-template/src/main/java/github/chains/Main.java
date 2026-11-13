package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;

/**
 * Generic Spoon transformation rule to fix breaking changes in Bouncy Castle crypto.tls package.
 * This rule handles the case where org.bouncycastle.crypto.tls was removed in version 1.67.
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
        
        // Build the model
        CtModel model = launcher.buildModel();

        // Find all imports from the problematic package and remove them
        model.getElements(
            element -> element instanceof CtImport && 
                ((CtImport) element).getReference() != null &&
                ((CtImport) element).getReference().toString().contains("crypto.tls")
        ).forEach(importElement -> {
            CtImport ctImport = (CtImport) importElement;
            CtReference reference = ctImport.getReference();
            
            if (reference != null && reference.toString().contains("crypto.tls")) {
                String oldImport = reference.toString();
                System.out.println("Removing deprecated import: " + oldImport);
                ctImport.delete();
            }
        });

        // Print a message about what needs to be done manually
        System.out.println("Deprecated crypto.tls imports have been removed.");
        System.out.println("Manual intervention needed for:");
        System.out.println("- TlsFatalAlert usage"); 
        System.out.println("- AlertDescription usage");
        System.out.println("These classes may need replacement with equivalent functionality.");
        
        System.out.println("Transformation complete.");
    }
}