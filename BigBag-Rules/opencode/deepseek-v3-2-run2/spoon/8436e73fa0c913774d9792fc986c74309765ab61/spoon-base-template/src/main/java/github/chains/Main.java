package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/source/code");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Migrating javax.jws to jakarta.jws in: " + sourceDir);
        System.out.println("This is a generic transformation for Jakarta EE migration.");
        System.out.println("Old API pattern: javax.jws.*");
        System.out.println("New API pattern: jakarta.jws.*");
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new JakartaJwsMigrationProcessor());
        
        CtModel model = launcher.buildModel();
        launcher.process();
        
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed!");
        System.out.println("Transformed files are in: " + launcher.getEnvironment().getSourceOutputDirectory());
        System.out.println("\nTo apply to other projects, change the source directory parameter.");
        System.out.println("This transformation is generic and works for any Java project");
        System.out.println("using javax.jws.* annotations that needs to migrate to jakarta.jws.*");
    }
    
    private static class JakartaJwsMigrationProcessor extends AbstractProcessor<CtCompilationUnit> {
        @Override
        public void process(CtCompilationUnit compilationUnit) {
            boolean modified = false;
            
            List<CtImport> importsToUpdate = new ArrayList<>();
            
            for (CtImport ctImport : compilationUnit.getImports()) {
                CtReference importedRef = ctImport.getReference();
                if (importedRef instanceof CtTypeReference) {
                    String qualifiedName = ((CtTypeReference<?>) importedRef).getQualifiedName();
                    if (qualifiedName != null && qualifiedName.startsWith("javax.jws.")) {
                        importsToUpdate.add(ctImport);
                    }
                }
            }
            
            for (CtImport ctImport : importsToUpdate) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                String qualifiedName = typeRef.getQualifiedName();
                String newName = qualifiedName.replace("javax.jws.", "jakarta.jws.");
                
                System.out.println("Migrating import: " + qualifiedName + " -> " + newName);
                
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newName);
                ctImport.setReference(newTypeRef);
                modified = true;
            }
            
            List<CtTypeReference<?>> typeRefs = Query.getElements(compilationUnit, 
                new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                    @Override
                    public boolean matches(CtTypeReference<?> typeRef) {
                        String qualifiedName = typeRef.getQualifiedName();
                        return qualifiedName != null && qualifiedName.startsWith("javax.jws.");
                    }
                });
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                String newName = qualifiedName.replace("javax.jws.", "jakarta.jws.");
                
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newName);
                typeRef.replace(newTypeRef);
                modified = true;
            }
            
            if (modified) {
                System.out.println("Updated file: " + compilationUnit.getFile().getName());
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtCompilationUnit compilationUnit) {
            for (CtImport ctImport : compilationUnit.getImports()) {
                CtReference importedRef = ctImport.getReference();
                if (importedRef instanceof CtTypeReference) {
                    String qualifiedName = ((CtTypeReference<?>) importedRef).getQualifiedName();
                    if (qualifiedName != null && qualifiedName.startsWith("javax.jws.")) {
                        return true;
                    }
                }
            }
            
            List<CtTypeReference<?>> typeRefs = Query.getElements(compilationUnit, 
                new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                    @Override
                    public boolean matches(CtTypeReference<?> typeRef) {
                        String qualifiedName = typeRef.getQualifiedName();
                        return qualifiedName != null && qualifiedName.startsWith("javax.jws.");
                    }
                });
            
            return !typeRefs.isEmpty();
        }
    }
}