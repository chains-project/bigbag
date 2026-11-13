package github.chains;

import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

import java.io.File;

/**
 * Generic Joda-Time API compatibility fix for Maven projects
 * This transformation fixes deprecated API usage in Joda-Time 2.12.5+
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-fixed");
        launcher.addProcessor(new JodaTimeFixProcessor());
        
        CtModel model = launcher.buildModel();
        launcher.process();
        
        System.out.println("Joda-Time API transformation completed for " + sourceDir);
    }
}