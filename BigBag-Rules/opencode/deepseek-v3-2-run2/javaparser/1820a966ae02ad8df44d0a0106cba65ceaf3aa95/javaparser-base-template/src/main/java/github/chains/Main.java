package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        // Find all pom.xml files
        List<Path> pomFiles = Files.walk(Paths.get("/workspace/pay-adminusers"))
                .filter(path -> path.toString().endsWith("pom.xml"))
                .collect(Collectors.toList());

        // Process each pom.xml file
        for (Path pomFile : pomFiles) {
            String content = Files.readString(pomFile);
            
            // Pattern to match SLF4J version properties and dependencies
            String updatedContent = content
                    // Update SLF4J version property if it exists
                    .replaceAll("(<slf4j\\.version>)[^<]*(</slf4j\\.version>)", "$12.0.0$2")
                    // Update specific SLF4J dependency versions
                    .replaceAll("(<artifactId>slf4j-api</artifactId>\\s*<version>)[^<]*(</version>)", "$12.0.0$2")
                    .replaceAll("(<artifactId>jul-to-slf4j</artifactId>\\s*<version>)[^<]*(</version>)", "$12.0.0$2")
                    .replaceAll("(<artifactId>log4j-over-slf4j</artifactId>\\s*<version>)[^<]*(</version>)", "$12.0.0$2")
                    .replaceAll("(<artifactId>jcl-over-slf4j</artifactId>\\s*<version>)[^<]*(</version>)", "$12.0.0$2");
            
            // Write updated content back
            if (!content.equals(updatedContent)) {
                Files.write(pomFile, updatedContent.getBytes());
                System.out.println("Updated SLF4J versions in: " + pomFile);
            }
        }
    }
}