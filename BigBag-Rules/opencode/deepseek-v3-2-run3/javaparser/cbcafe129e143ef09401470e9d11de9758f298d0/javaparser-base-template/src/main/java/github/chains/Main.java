package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("\nApplies transformation to fix logback-classic 1.4.6 compatibility");
            System.err.println("with SLF4J 1.x. The transformation:");
            System.err.println("1. Removes casts to ch.qos.logback.classic.Logger");
            System.err.println("2. Replaces setLevel() and addAppender() calls with reflection");
            System.err.println("3. Makes code compile with both SLF4J 1.x and 2.x");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        LogbackTransformation.transformDirectory(sourceDir);
    }
}