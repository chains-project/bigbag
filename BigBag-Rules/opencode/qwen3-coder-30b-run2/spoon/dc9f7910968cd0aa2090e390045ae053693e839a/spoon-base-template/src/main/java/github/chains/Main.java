package github.chains;

import spoon.Launcher;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.io.FileNotFoundException;

public class Main {
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(SpoonResourceHelper.createResource(new File(sourceDir)));
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Process with our custom processor
        launcher.addProcessor(new FlywayApiMigrationProcessor());
        
        // Run the processing
        CtModel model = launcher.buildModel();
        launcher.process();
        
        System.out.println("Transformation completed.");
    }
}