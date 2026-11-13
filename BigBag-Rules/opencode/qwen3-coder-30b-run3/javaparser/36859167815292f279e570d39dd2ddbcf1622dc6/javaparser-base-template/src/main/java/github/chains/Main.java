package github.chains;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying FOP Factory transformation to: " + sourceDir);
        FopFactoryFixer.fixFopFactoryNewInstanceCalls(sourceDir);
        System.out.println("Transformation complete!");
    }
}
