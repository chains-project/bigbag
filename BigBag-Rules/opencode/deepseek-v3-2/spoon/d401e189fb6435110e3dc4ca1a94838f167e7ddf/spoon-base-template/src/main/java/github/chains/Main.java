package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.ImportCleaner;
import spoon.support.StandardEnvironment;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <source-directory>");
            System.err.println("Example: java -cp target/spoon-base-1.0-SNAPSHOT.jar github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying logback compatibility transformation to: " + sourceDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setCopyResources(false);
            
            // Add processor
            launcher.addProcessor(new LogbackCompatibilityTransformation());
            
            // Build model and apply transformation
            launcher.run();
            
            System.out.println("Transformation completed successfully");
            System.out.println("Summary:");
            System.out.println("- Detects casts to ch.qos.logback.classic.Logger");
            System.out.println("- Comments out problematic code with explanation");
            System.out.println("- Provides generic fix suggestions for any project");
            System.out.println("- Output files are in the same directory with fixes applied");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}