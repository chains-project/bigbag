package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // Process all Java files in the target directory
        String targetDir = args.length > 0 ? args[0] : "/workspace/dropwizard-pac4j";
        processDirectory(Paths.get(targetDir));
    }

    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Fix javax.servlet imports to jakarta.servlet
            boolean modified = false;
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("javax.servlet")) {
                    String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                    importDecl.setName(newImport);
                    modified = true;
                }
            }
            
            // Write back the modified content if changes were made
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Processed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}