package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Xpp3DomTransformer {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Xpp3DomTransformer <project-root>");
            System.exit(1);
        }
        
        String projectRoot = args[0];
        System.out.println("Processing project: " + projectRoot);
        
        // Old and new type references
        String oldImport = "import org.codehaus.plexus.util.xml.Xpp3Dom;";
        String newImport = "import org.apache.maven.shared.utils.xml.Xpp3Dom;";
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
                    String line = lines.get(i);
                    // Check for import
                    if (line.trim().equals(oldImport)) {
                        lines.set(i, newImport);
                        System.out.println("Updated import in: " + javaFile);
                        modified = true;
                    }
                    // Check for type references (but not in imports we already handled)
                    else if (line.contains(oldType) && !line.trim().startsWith("import")) {
                        lines.set(i, line.replace(oldType, newType));
                        System.out.println("Updated type reference in: " + javaFile + " line " + (i+1));
                        modified = true;
                    }
                }
                
                if (modified) {
                    Files.write(javaFile, lines);
                }
            }
        }
        
        System.out.println("Transformation complete!");
    }
}