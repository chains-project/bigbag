package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Example usage of the HamcrestTransformation.
 */
public class HamcrestTransformationExample {
    public static void main(String[] args) {
        System.out.println("Hamcrest Transformation Example");
        
        if (args.length == 0) {
            System.out.println("Usage: java HamcrestTransformationExample <filepath>");
            return;
        }
        
        String filepath = args[0];
        
        try {
            // Check if file has hamcrest imports
            if (HamcrestTransformation.hasHamcrestImports(filepath)) {
                System.out.println("File contains hamcrest imports - transforming...");
                HamcrestTransformation.applyTransformation(filepath);
                System.out.println("Transformation complete.");
            } else {
                System.out.println("No hamcrest imports found in file.");
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }
}