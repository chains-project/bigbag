package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.Set;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar spoon-transformation.jar <project-path>");
            System.out.println("  <project-path>: Path to the Maven project source directory");
            System.exit(1);
        }
        
        String projectPath = args[0];
        String outputPath = projectPath;
        if (args.length > 1) {
            outputPath = args[1];
        }
        
        System.out.println("Applying Jakarta EE migration transformation to: " + projectPath);
        System.out.println("Output directory: " + outputPath);
        
        Launcher launcher = new Launcher();
        
        launcher.addInputResource(projectPath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        int importChanges = migrateImports(model);
        int typeRefChanges = migrateTypeReferences(model);
        
        System.out.println("Transformation completed:");
        System.out.println("  - Updated " + importChanges + " import statements");
        System.out.println("  - Updated " + typeRefChanges + " type references");
        
        launcher.setSourceOutputDirectory(outputPath);
        launcher.prettyprint();
        
        System.out.println("Files written to: " + outputPath);
    }
    
    private static int migrateImports(CtModel model) {
        int changes = 0;
        
        for (CtType<?> type : model.getAllTypes()) {
            for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
                try {
                    if (imp.getReference() != null) {
                        String importString = imp.getReference().toString();
                        
                        if (importString.startsWith("javax.annotation")) {
                            String newImport = importString.replace("javax.annotation", "jakarta.annotation");
                            
                            CtTypeReference<?> newRef = imp.getFactory().Type().createReference(newImport);
                            imp.setReference(newRef);
                            changes++;
                            
                            System.out.println("  Updated import: " + importString + " -> " + newImport);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("  Warning: Failed to process import: " + e.getMessage());
                }
            }
        }
        
        return changes;
    }
    
    private static int migrateTypeReferences(CtModel model) {
        int changes = 0;
        
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            try {
                String qualifiedName = typeRef.getQualifiedName();
                
                if (qualifiedName != null && qualifiedName.startsWith("javax.annotation")) {
                    String newQualifiedName = qualifiedName.replace("javax.annotation", "jakarta.annotation");
                    
                    typeRef.getFactory().Type().createReference(newQualifiedName);
                    typeRef.setSimpleName(newQualifiedName.substring(newQualifiedName.lastIndexOf('.') + 1));
                    changes++;
                    
                    System.out.println("  Updated type reference: " + qualifiedName + " -> " + newQualifiedName);
                }
            } catch (Exception e) {
                
            }
        }
        
        return changes;
    }
}