package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generic transformation to fix breaking changes in jaxb2-basics-runtime dependency.
 * Specifically addresses the removal of getInstance() methods from strategy classes.
 * 
 * This transformation replaces calls to:
 *   JAXBToStringStrategy.getInstance() 
 * With:
 *   DefaultToStringStrategy.INSTANCE
 * 
 * And similar for other strategy classes.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation for jaxb2-basics-runtime breaking changes");
        
        // Set the source directory to transform (parameterizable)
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/billy";
        String outputDirectory = args.length > 1 ? args[1] : "/workspace/billy-transformed";
        
        System.out.println("Source directory: " + sourceDirectory);
        System.out.println("Output directory: " + outputDirectory);
        
        // Create output directory if it doesn't exist
        new File(outputDirectory).mkdirs();
        
        // Copy all files first
        try {
            copyDirectory(new File(sourceDirectory), new File(outputDirectory));
        } catch (IOException e) {
            System.err.println("Error copying files: " + e.getMessage());
            return;
        }
        
        // Now apply regex-based fixes to the copied files
        fixGeneratedFiles(outputDirectory);
        
        System.out.println("Transformation complete. Fixed files in " + outputDirectory);
    }
    
    private static void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File tgtFile = new File(target, file);
                    if (srcFile.isDirectory()) {
                        copyDirectory(srcFile, tgtFile);
                    } else {
                        Files.copy(srcFile.toPath(), tgtFile.toPath());
                    }
                }
            }
        } else {
            Files.copy(source.toPath(), target.toPath());
        }
    }
    
    private static void fixGeneratedFiles(String directory) {
        try {
            // Walk through all Java files in the directory
            Files.walk(Paths.get(directory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::fixFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
    
    private static void fixFile(java.nio.file.Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean changed = false;
            
            // Fix JAXBToStringStrategy.getInstance() calls
            content = content.replaceAll(
                "org\\.jvnet\\.jaxb2_commons\\.lang\\.JAXBToStringStrategy\\.getInstance\\(\\)",
                "org.jvnet.jaxb2_commons.lang.DefaultToStringStrategy.INSTANCE"
            );
            changed = true;
            
            // Fix other strategy classes
            content = content.replaceAll(
                "org\\.jvnet\\.jaxb2_commons\\.lang\\.JAXBEqualsStrategy\\.getInstance\\(\\)",
                "org.jvnet.jaxb2_commons.lang.DefaultEqualsStrategy.INSTANCE"
            );
            changed = true;
            
            content = content.replaceAll(
                "org\\.jvnet\\.jaxb2_commons\\.lang\\.JAXBHashCodeStrategy\\.getInstance\\(\\)",
                "org.jvnet.jaxb2_commons.lang.DefaultHashCodeStrategy.INSTANCE"
            );
            changed = true;
            
            content = content.replaceAll(
                "org\\.jvnet\\.jaxb2_commons\\.lang\\.JAXBCopyStrategy\\.getInstance\\(\\)",
                "org.jvnet.jaxb2_commons.lang.DefaultCopyStrategy.INSTANCE"
            );
            changed = true;
            
            content = content.replaceAll(
                "org\\.jvnet\\.jaxb2_commons\\.lang\\.JAXBMergeStrategy\\.getInstance\\(\\)",
                "org.jvnet.jaxb2_commons.lang.DefaultMergeStrategy.INSTANCE"
            );
            changed = true;
            
            if (changed) {
                Files.write(filePath, content.getBytes());
                System.out.println("Fixed file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}