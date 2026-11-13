package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple utility to demonstrate how to fix FilterBuilder.apply() calls
 * This shows exactly what needs to be transformed
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        System.out.println("Simple JavaParser transformation demo:");
        System.out.println("This would transform:");
        System.out.println("  new FilterBuilder().include(\".+\\\\.class\").apply(s)");
        System.out.println("Into:");
        System.out.println("  s.matches(\".+\\\\.class\")");
        System.out.println();
        System.out.println("Usage: run with source directory as argument");
        
        // Just show an example of the transformation needed
        if (args.length >= 1) {
            String sourceDirectory = args[0];
            Path dir = Paths.get(sourceDirectory);
            
            if (Files.exists(dir)) {
                System.out.println("Would process files in: " + sourceDirectory);
            } else {
                System.err.println("Source directory does not exist: " + sourceDirectory);
            }
        }
    }
}
