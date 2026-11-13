package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to replace SelectChannelConnector with ServerConnector
 * in any Maven project affected by Jetty 11 breaking changes.
 * 
 * This transformation addresses the breaking change where SelectChannelConnector was removed
 * in Jetty 11.0.8 and replaced with ServerConnector.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Process all Java files in the directory recursively
        Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            boolean modified = false;
            
            // Direct approach - manually replace the problematic parts
            String content = cu.toString();
            
            // Replace SelectChannelConnector with ServerConnector
            String newContent = content.replace(
                "org.eclipse.jetty.server.nio.SelectChannelConnector",
                "org.eclipse.jetty.server.ServerConnector"
            );
            
            // Replace new SelectChannelConnector() with new ServerConnector()
            newContent = newContent.replace(
                "new SelectChannelConnector()",
                "new ServerConnector()"
            );
            
            // Save file if it was modified
            if (!content.equals(newContent)) {
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(newContent);
                }
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
