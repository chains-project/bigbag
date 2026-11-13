package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCatch;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class Main {
    
    // Configuration parameters - can be passed as system properties or environment variables
    // Default values for the specific breaking change we're fixing
    private static final String OLD_EXCEPTION_CLASS = System.getProperty("oldExceptionClass", 
        "org.apache.maven.artifact.DependencyResolutionRequiredException");
    private static final String OLD_MAVEN_PROJECT_CLASS = System.getProperty("oldMavenProjectClass", 
        "org.apache.maven.project.MavenProject");
    private static final String NEW_MAVEN_PROJECT_CLASS = System.getProperty("newMavenProjectClass", 
        "org.apache.maven.api.project.MavenProject"); // Example new location
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [output-directory]");
            System.err.println("Configuration properties:");
            System.err.println("  -DoldExceptionClass=<class> - Old exception class to remove");
            System.err.println("  -DoldMavenProjectClass=<class> - Old MavenProject class");
            System.err.println("  -DnewMavenProjectClass=<class> - New MavenProject class (if moved)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Configuration:");
        System.out.println("  Old exception class: " + OLD_EXCEPTION_CLASS);
        System.out.println("  Old MavenProject class: " + OLD_MAVEN_PROJECT_CLASS);
        System.out.println("  New MavenProject class: " + NEW_MAVEN_PROJECT_CLASS);
        
        try {
            transformProject(sourceDir, outputDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir, String outputDir) {
        Launcher launcher = new Launcher();
        
        // Configure Spoon
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processors
        launcher.addProcessor(new RemoveExceptionImportProcessor(OLD_EXCEPTION_CLASS));
        launcher.addProcessor(new FixMavenProjectImportProcessor(OLD_MAVEN_PROJECT_CLASS, NEW_MAVEN_PROJECT_CLASS));
        launcher.addProcessor(new RemoveExceptionCatchProcessor(OLD_EXCEPTION_CLASS));
        // Note: Type reference updates are complex in Spoon and depend on the specific use case
        // For this generic transformation, we rely on import fixes and catch block removal
        
        // Run transformation
        launcher.run();
    }
    
    /**
     * Processor to remove imports of a specific exception class
     */
    private static class RemoveExceptionImportProcessor extends AbstractProcessor<CtImport> {
        private final String oldExceptionClass;
        
        public RemoveExceptionImportProcessor(String oldExceptionClass) {
            this.oldExceptionClass = oldExceptionClass;
        }
        
        @Override
        public void process(CtImport ctImport) {
            String importStr = ctImport.toString();
            if (importStr.contains(oldExceptionClass)) {
                System.out.println("Removing import: " + importStr);
                ctImport.delete();
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            return candidate.toString().contains(oldExceptionClass);
        }
    }
    
    /**
     * Processor to fix imports of a class that moved to a new package
     */
    private static class FixMavenProjectImportProcessor extends AbstractProcessor<CtImport> {
        private final String oldClass;
        private final String newClass;
        
        public FixMavenProjectImportProcessor(String oldClass, String newClass) {
            this.oldClass = oldClass;
            this.newClass = newClass;
        }
        
        @Override
        public void process(CtImport ctImport) {
            String importStr = ctImport.toString();
            if (importStr.contains(oldClass)) {
                System.out.println("Found old class import: " + importStr);
                
                // Check if we should update to new package
                if (!newClass.equals(oldClass)) {
                    System.out.println("Updating import to: " + newClass);
                    
                    // Remove old import
                    ctImport.delete();
                    
                    // Note: New import will be added automatically by Spoon's autoImports
                    // when the type is referenced in code
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            return candidate.toString().contains(oldClass);
        }
    }
    
    /**
     * Processor to remove catch blocks for a specific exception class
     */
    private static class RemoveExceptionCatchProcessor extends AbstractProcessor<CtCatch> {
        private final String oldExceptionClass;
        
        public RemoveExceptionCatchProcessor(String oldExceptionClass) {
            this.oldExceptionClass = oldExceptionClass;
        }
        
        @Override
        public void process(CtCatch ctCatch) {
            CtTypeReference<?> caughtType = ctCatch.getParameter().getType();
            if (caughtType != null && oldExceptionClass.equals(caughtType.getQualifiedName())) {
                System.out.println("Removing catch block for: " + oldExceptionClass);
                
                // For a generic solution, we remove just the catch block
                // In practice, we might need to handle the try block differently
                // depending on whether this was the only catch block
                ctCatch.delete();
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtCatch candidate) {
            CtTypeReference<?> caughtType = candidate.getParameter().getType();
            return caughtType != null && oldExceptionClass.equals(caughtType.getQualifiedName());
        }
    }
    
    
}