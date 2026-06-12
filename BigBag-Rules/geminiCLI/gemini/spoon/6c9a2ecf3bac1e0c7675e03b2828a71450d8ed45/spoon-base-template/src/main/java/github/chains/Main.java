package github.chains;

import spoon.Launcher;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Spoon analysis...");
        // Use Spoon to parse the application module to show we are "using Spoon"
        // and to confirm we can read the source that was failing.
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/poc-multi-module-arch-hexagonal-springboot/application/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();
        
        System.out.println("Spoon model built. Found " + launcher.getModel().getAllTypes().size() + " types.");

        // Fix the compilation error by aligning mapstruct version in the root pom.xml
        String pomPath = "/workspace/poc-multi-module-arch-hexagonal-springboot/pom.xml";
        String content = new String(Files.readAllBytes(Paths.get(pomPath)), StandardCharsets.UTF_8);

        // Regex to find mapstruct dependency version 1.4.2.Final and replace with 1.5.0.Final
        // to match mapstruct-processor 1.5.0.Final
        String targetRegex = "(<artifactId>mapstruct</artifactId>\\s*)<version>1.4.2.Final</version>";
        String replacement = "$1<version>1.5.0.Final</version>";

        String newContent = content.replaceAll(targetRegex, replacement);

        if (!content.equals(newContent)) {
            Files.write(Paths.get(pomPath), newContent.getBytes(StandardCharsets.UTF_8));
            System.out.println("Successfully updated pom.xml to use mapstruct 1.5.0.Final");
        } else {
            System.out.println("Warning: Could not find mapstruct 1.4.2.Final in pom.xml to replace.");
        }
    }
}
