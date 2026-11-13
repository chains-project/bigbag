package github.chains;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /workspace/nem/nis/src/main/java");
            System.exit(1);
        }

        // Use the FlywayTransformation class
        FlywayTransformation.main(args);
    }
}