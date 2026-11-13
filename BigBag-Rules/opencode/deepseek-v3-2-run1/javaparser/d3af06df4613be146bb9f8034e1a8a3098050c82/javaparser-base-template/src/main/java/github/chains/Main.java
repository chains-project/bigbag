package github.chains;

import java.nio.file.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("This tool applies generic transformations for Jetty 9/10 to Jetty 11 migration.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Jetty11Transformation.main(args);
    }
}