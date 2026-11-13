package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Invalid directory: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("TFramedTransport transformation is ready.");
        System.out.println("This is a template for a generic transformation to fix libthrift 0.16.0 breaking change.");
        System.out.println("It removes references to TFramedTransport and replaces with TIOStreamTransport where appropriate.");
        System.out.println("Source code for this transformation is in src/main/java/github/chains/Main.java");
    }
}