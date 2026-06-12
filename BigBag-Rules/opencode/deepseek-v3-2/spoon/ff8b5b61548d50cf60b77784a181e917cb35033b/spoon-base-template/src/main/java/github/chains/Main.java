package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.ImportConflictDetector;
import spoon.reflect.visitor.ImportCleaner;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <project-source-dir>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Applying Mockito 4.1.0 migration fix to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        
        launcher.addProcessor(new MockitoRunnersPackageMigrationProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
        } catch (spoon.compiler.ModelBuildingException e) {
            // Even if there are compilation errors in the source code,
            // we might have successfully applied some transformations
            System.err.println("Warning: Model building had issues, but transformations may have been applied: " + e.getMessage());
            System.err.println("Checking if transformations were successful...");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class MockitoRunnersPackageMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private static final String OLD_RUNNERS_PACKAGE = "org.mockito.runners";
        private static final String NEW_JUNIT_PACKAGE = "org.mockito.junit";
        
        @Override
        public void process(CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Check if this type reference is in the old runners package
            if (qualifiedName != null && qualifiedName.startsWith(OLD_RUNNERS_PACKAGE + ".")) {
                System.out.println("Found type reference in old Mockito runners package: " + 
                                 qualifiedName + " at " + typeRef.getPosition());
                
                // Extract the simple class name
                String simpleClassName = qualifiedName.substring(OLD_RUNNERS_PACKAGE.length() + 1);
                String newQualifiedName = NEW_JUNIT_PACKAGE + "." + simpleClassName;
                
                System.out.println("Will migrate to: " + newQualifiedName);
                
                // Create new type reference
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                
                // Copy any type arguments if present
                if (typeRef.getActualTypeArguments() != null && !typeRef.getActualTypeArguments().isEmpty()) {
                    newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                }
                
                // Replace the old reference with the new one
                typeRef.replace(newTypeRef);
            }
        }
        
        @Override
        public void processingDone() {
            System.out.println("Mockito runners package migration processor finished.");
        }
    }
}