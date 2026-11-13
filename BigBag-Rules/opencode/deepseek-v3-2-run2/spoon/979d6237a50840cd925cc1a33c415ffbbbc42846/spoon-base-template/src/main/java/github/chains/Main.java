package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [<output-directory]");
            System.err.println("If output-directory is not provided, transformations are applied in-place.");
            return;
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args.length > 1 ? args[1] : sourceDirectory;
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        
        // Add processors to fix Struts2 package changes
        launcher.addProcessor(new Struts2TypeRefFixProcessor());
        launcher.addProcessor(new Struts2ImportFixProcessor());
        
        launcher.run();
    }
    
    static class Struts2TypeRefFixProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        @Override
        public void process(CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            
            if (qualifiedName != null && qualifiedName.startsWith("org.apache.struts2.dispatcher.ng.filter.")) {
                String className = qualifiedName.substring("org.apache.struts2.dispatcher.ng.filter.".length());
                String newQualifiedName = "org.apache.struts2.dispatcher.filter." + className;
                
                CtTypeReference<?> newTypeRef = getFactory().createReference(newQualifiedName);
                typeRef.replace(newTypeRef);
                
                System.out.println("Fixed type reference: " + qualifiedName + " -> " + newQualifiedName);
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            String qualifiedName = candidate.getQualifiedName();
            return qualifiedName != null && qualifiedName.startsWith("org.apache.struts2.dispatcher.ng.filter.");
        }
    }
    
    static class Struts2ImportFixProcessor extends AbstractProcessor<CtCompilationUnit> {
        
        @Override
        public void process(CtCompilationUnit compilationUnit) {
            List<CtImport> imports = compilationUnit.getImports();
            for (CtImport importDecl : imports) {
                Object imported = importDecl.getReference();
                if (imported instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) imported;
                    String qualifiedName = typeRef.getQualifiedName();
                    
                    if (qualifiedName != null && qualifiedName.startsWith("org.apache.struts2.dispatcher.ng.filter.")) {
                        String className = qualifiedName.substring("org.apache.struts2.dispatcher.ng.filter.".length());
                        String newQualifiedName = "org.apache.struts2.dispatcher.filter." + className;
                        
                        CtTypeReference<?> newTypeRef = getFactory().createReference(newQualifiedName);
                        importDecl.setReference(newTypeRef);
                        
                        System.out.println("Fixed import in " + compilationUnit.getFile().getName() + 
                                           ": " + qualifiedName + " -> " + newQualifiedName);
                    }
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtCompilationUnit candidate) {
            System.out.println("DEBUG: Checking compilation unit: " + candidate.getFile().getName());
            return true; // Process all compilation units
        }
    }
}