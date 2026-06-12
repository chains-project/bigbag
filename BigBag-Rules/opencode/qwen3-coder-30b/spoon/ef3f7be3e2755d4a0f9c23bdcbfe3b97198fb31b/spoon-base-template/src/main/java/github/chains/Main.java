package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        
        // Add the processors to fix tinspin-indexes API breakages
        launcher.addProcessor(new FixTinspinIndexBreakages());
        launcher.addProcessor(new FixTinspinImports());
        
        // Process the code
        CtModel model = launcher.buildModel();
        launcher.process();
        
        // Output the results
        System.out.println("Transformation complete. Output written to " + sourceDirectory + "-transformed");
    }
}