package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.out.println("Example: java -cp target/classes:~/.m2/repository/fr/inria/gforge/spoon/spoon-core/11.2.1/spoon-core-11.2.1.jar github.chains.Main /workspace/nem/nis/src/main/java");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Flyway API Migration Transformer");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("This is a template for the transformation rule.");
        System.out.println("For actual usage, you would:");
        System.out.println("1. Build with: mvn compile");
        System.out.println("2. Run with: java -cp ... github.chains.Main <source-directory>");
        System.out.println("3. The FlywayMigratorProcessor would handle the transformation");
        
        // Demonstrate that we can build the model
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.addInputResource(sourceDir);
            launcher.addProcessor(new FlywayMigratorProcessor());
            
            CtModel model = launcher.buildModel();
            System.out.println("Model built successfully - transformation ready");
            
        } catch (Exception e) {
            System.err.println("Error building model: " + e.getMessage());
        }
    }
}