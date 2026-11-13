package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        launcher.setSourceOutputDirectory(sourceDir + "/fixed");

        // Add the source directory
        launcher.addInputResource(sourceDir);

        // Set the target Java version
        launcher.getEnvironment().setComplianceLevel(8);

        // Build the model
        launcher.buildModel();

        // Apply transformations
        applyTransformations(launcher);

        // Print statistics
        System.out.println("Transformation complete.");
    }

    private static void applyTransformations(Launcher launcher) {
        // Simple transformation to demonstrate concept
        System.out.println("Generic transformation applied to: " + launcher.getModel().getRootPackage().getSimpleName());
    }
}