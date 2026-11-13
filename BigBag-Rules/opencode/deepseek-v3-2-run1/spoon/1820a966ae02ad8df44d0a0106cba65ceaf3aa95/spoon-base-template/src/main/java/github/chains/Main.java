package github.chains;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <sourceDirectory> <outputDirectory>");
            System.err.println("\nOr use the dedicated transformation class:");
            System.err.println("java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.LogbackCompatibilityTransformation <sourceDirectory> <outputDirectory>");
            System.exit(1);
        }
        
        // Run the transformation
        LogbackCompatibilityTransformation.main(args);
    }
}