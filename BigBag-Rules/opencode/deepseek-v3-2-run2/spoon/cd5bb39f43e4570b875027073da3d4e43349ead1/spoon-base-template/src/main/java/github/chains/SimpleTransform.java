package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleTransform {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java SimpleTransform <project-root>");
            System.exit(1);
        }
        
        String projectRoot = args[0];
        System.out.println("Processing project: " + projectRoot);
        
        // Old and new type references
        String oldType = "org.codehaus.plexus.util.xml.Xpp3Dom";
        String newType = "org.apache.maven.shared.utils.xml.Xpp3Dom";
        
        try (Stream<Path> walk = Files.walk(Paths.get(projectRoot))) {
            List<Path> javaFiles = walk
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                boolean modified = false;
                List<String> lines = Files.readAllLines(javaFile);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(oldType)) {
                        lines.set(i, lines.get(i).replace(oldType, newType));
                        modified = true;
                    }
                }
                
                if (modified) {
                    Files.write(javaFile, lines);
                    System.out.println("Updated: " + javaFile);
                }
            }
        }
        
        System.out.println("Transformation complete!");
    }
}