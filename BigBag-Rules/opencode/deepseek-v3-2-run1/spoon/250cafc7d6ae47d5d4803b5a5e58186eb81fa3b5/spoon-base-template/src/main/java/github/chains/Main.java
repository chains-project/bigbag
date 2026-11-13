package github.chains;

import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.code.CtConstructorCall;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("\nThis transformation fixes Flyway API breaking changes from");
            System.err.println("pre-9.21.0 to 9.21.0+ by converting:");
            System.err.println("  new Flyway() + setter calls");
            System.err.println("to:");
            System.err.println("  new Flyway(Flyway.configure()...load())");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        // Create and configure Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add our Flyway transformer processor
        Processor<CtConstructorCall<?>> processor = new FlywayTransformer();
        launcher.addProcessor(processor);
        
        // Build model and apply transformations
        System.out.println("Building code model...");
        launcher.buildModel();
        
        System.out.println("Applying transformations...");
        launcher.process();
        
        // Output transformed code
        System.out.println("Writing transformed code...");
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete!");
        System.out.println("The following changes were made:");
        System.out.println("1. new Flyway() → new Flyway(Flyway.configure()...load())");
        System.out.println("2. Removed setDataSource(), setClassLoader(), setLocations(), setValidateOnMigrate() calls");
        System.out.println("3. Converted setters to fluent API: setXxx(value) → .xxx(value)");
    }
}