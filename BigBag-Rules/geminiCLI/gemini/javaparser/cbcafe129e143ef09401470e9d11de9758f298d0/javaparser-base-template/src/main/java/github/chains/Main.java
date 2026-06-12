package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        Path pomPath = Paths.get("/workspace/pay-adminusers/pom.xml");
        List<String> lines = Files.readAllLines(pomPath);
        
        List<String> newLines = lines.stream().map(line -> {
            if (line.contains("<version>1.4.6</version>")) {
                // Check if the previous lines roughly correspond to logback-classic to avoid replacing other 1.4.6 versions
                // But simplified: checking context in stream is hard. 
                // Let's use a replace on the full string content instead.
                return line;
            }
            return line;
        }).collect(Collectors.toList());
        
        // Simpler approach: read as string
        String content = new String(Files.readAllBytes(pomPath));
        String oldDependency = 
            "        <dependency>\n" +
            "            <groupId>ch.qos.logback</groupId>\n" +
            "            <artifactId>logback-classic</artifactId>\n" +
            "            <version>1.4.6</version>\n" +
            "        </dependency>";
        
        String newDependency = 
            "        <dependency>\n" +
            "            <groupId>ch.qos.logback</groupId>\n" +
            "            <artifactId>logback-classic</artifactId>\n" +
            "            <version>1.2.12</version>\n" +
            "        </dependency>";
            
        if (content.contains(oldDependency)) {
            content = content.replace(oldDependency, newDependency);
            Files.write(pomPath, content.getBytes());
            System.out.println("Fixed pom.xml: Downgraded logback-classic to 1.2.12");
        } else { // Fallback for different whitespace/formatting
             String regex = "(<groupId>ch.qos.logback</groupId>\\s*<artifactId>logback-classic</artifactId>\\s*<version>)1.4.6(</version>)";
             String newContent = content.replaceAll(regex, "$11.2.12$2");
             if (!content.equals(newContent)) {
                 Files.write(pomPath, newContent.getBytes());
                 System.out.println("Fixed pom.xml with regex: Downgraded logback-classic to 1.2.12");
             } else {
                 System.out.println("Could not find logback-classic 1.4.6 to replace.");
             }
        }
    }
}