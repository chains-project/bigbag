package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Flyway API transformation for Maven projects.
 * This transformation fixes breaking changes in Flyway dependency updates.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <inputPath> <outputPath>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        try {
            System.out.println("Processing files from: " + inputPath);
            System.out.println("Output will be written to: " + outputPath);
            
            Launcher launcher = new Launcher();
            launcher.addInputResource(inputPath);
            launcher.setSourceOutputDirectory(outputPath);

            // Add the processor to fix Flyway API calls
            launcher.addProcessor(new FlywayApiProcessor());

            // Process the model
            launcher.process();

            // Print success message
            System.out.println("Flyway transformation completed successfully.");
            System.out.println("Transformed files are in: " + outputPath);

        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}