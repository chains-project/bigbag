package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Path pomPath = Paths.get("/workspace/OCR4all/pom.xml");
        try {
            List<String> lines = Files.readAllLines(pomPath);
            List<String> newLines = lines.stream().map(line -> {
                if (line.trim().contains("<artifactId>jackson-core</artifactId>")) {
                    return line;
                }
                // We need to be careful not to replace other 2.10.0 versions if any
                // But checking the previous line or context is hard in stream map without state.
                // Let's iterate with index.
                return line;
            }).collect(Collectors.toList());

            for (int i = 0; i < newLines.size(); i++) {
                String line = newLines.get(i);
                if (line.trim().contains("<artifactId>jackson-core</artifactId>")) {
                    // Check next line for version
                    if (i + 1 < newLines.size()) {
                        String nextLine = newLines.get(i + 1);
                        if (nextLine.trim().contains("<version>2.10.0</version>")) {
                            newLines.set(i + 1, nextLine.replace("2.10.0", "2.13.4"));
                            System.out.println("Updated jackson-core version to 2.13.4");
                        }
                    }
                }
            }

            Files.write(pomPath, newLines);
            System.out.println("pom.xml updated successfully.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}