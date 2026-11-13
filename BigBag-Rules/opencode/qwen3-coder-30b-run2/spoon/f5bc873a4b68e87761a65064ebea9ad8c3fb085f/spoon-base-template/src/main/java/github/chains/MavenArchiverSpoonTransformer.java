package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation for Maven Archiver API breaking changes.
 * This transformation helps migrate code affected by breaking changes in Maven Archiver 3.6.0.
 */
public class MavenArchiverSpoonTransformer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MavenArchiverSpoonTransformer <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        // Process the source directory
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setLevel("INFO");
        launcher.addInputResource(sourceDir);
        launcher.addProcessor(new MavenArchiverProcessor());
        
        launcher.buildModel();
        launcher.process();
    }
}