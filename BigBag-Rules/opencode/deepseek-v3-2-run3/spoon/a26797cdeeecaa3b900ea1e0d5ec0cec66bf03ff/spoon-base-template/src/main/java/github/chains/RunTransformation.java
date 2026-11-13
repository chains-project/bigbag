package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import java.util.List;

public class RunTransformation {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java RunTransformation <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addProcessor(new Main());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir); // Overwrite original files
        
        try {
            System.out.println("Applying transformation to: " + sourceDir);
            launcher.run();
            System.out.println("Transformation completed");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}