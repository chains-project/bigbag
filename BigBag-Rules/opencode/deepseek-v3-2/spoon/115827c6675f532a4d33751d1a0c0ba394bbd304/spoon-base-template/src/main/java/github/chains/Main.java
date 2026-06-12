package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.TokenWriter;
import spoon.support.JavaOutputProcessor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying javax.servlet to jakarta.servlet migration to: " + sourceDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Apply transformation using a custom processor
            ServletMigrationProcessor processor = new ServletMigrationProcessor();
            model.processWith(processor);
            
            // Write the transformed code back
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully! Made " + processor.getChanges() + " changes.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor that migrates javax.servlet references to jakarta.servlet
     */
    static class ServletMigrationProcessor extends spoon.processing.AbstractProcessor<CtType<?>> {
        private int changes = 0;
        
        public int getChanges() {
            return changes;
        }
        
        @Override
        public void process(CtType<?> type) {
            // Process imports in the compilation unit
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            if (cu != null) {
                processImports(cu);
            }
            
            // Process all type references in this type
            processTypeReferences(type);
        }
        
        private void processImports(CtCompilationUnit cu) {
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            List<CtImport> newImports = new ArrayList<>();
            
            for (CtImport imp : imports) {
                String importStr = imp.toString();
                if (importStr.contains("javax.servlet")) {
                    // Create new import with jakarta.servlet
                    String newImportStr = importStr.replace("javax.servlet", "jakarta.servlet");
                    
                    // Parse the new import
                    try {
                        Launcher tempLauncher = new Launcher();
                        tempLauncher.getEnvironment().setNoClasspath(false);
                        tempLauncher.getEnvironment().setAutoImports(false);
                        
                        // Create a simple class to parse the import
                        String dummyCode = "package dummy;\n" + newImportStr + "\nclass Dummy {}";
                        CtType<?> dummyType = Launcher.parseClass(dummyCode);
                        
                        if (dummyType != null) {
                            CtCompilationUnit dummyCu = dummyType.getPosition().getCompilationUnit();
                            if (dummyCu != null) {
                                // Remove old import from original CU
                                cu.getImports().remove(imp);
                                
                                // Add new imports from dummy CU
                                for (CtImport newImport : dummyCu.getImports()) {
                                    cu.getImports().add(newImport);
                                }
                                
                                changes++;
                                System.out.println("Import replaced: " + importStr + " -> " + newImportStr);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to process import: " + importStr);
                    }
                }
            }
        }
        
        private void processTypeReferences(CtElement element) {
            // Find all type references in the element
            element.getElements(new spoon.reflect.visitor.filter.TypeFilter<CtTypeReference<?>>(CtTypeReference.class))
                .forEach(this::processTypeReference);
        }
        
        private void processTypeReference(CtTypeReference<?> ref) {
            String qualifiedName = ref.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("javax.servlet.")) {
                String newName = qualifiedName.replace("javax.servlet", "jakarta.servlet");
                
                try {
                    // Create new type reference
                    CtTypeReference<?> newRef = getFactory().Type().createReference(newName);
                    
                    // Replace the reference
                    ref.replace(newRef);
                    
                    changes++;
                    System.out.println("Type reference replaced: " + qualifiedName + " -> " + newName);
                } catch (Exception e) {
                    System.err.println("Failed to replace type reference: " + qualifiedName);
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtType<?> candidate) {
            // Process all types
            return true;
        }
    }
}