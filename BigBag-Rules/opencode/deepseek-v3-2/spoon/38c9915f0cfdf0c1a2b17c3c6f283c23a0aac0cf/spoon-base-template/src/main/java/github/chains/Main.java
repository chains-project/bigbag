package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class Main {
    
    private static final Map<String, String> PACKAGE_MOVES = new HashMap<>();
    private static final Map<String, String> CLASS_RENAMES = new HashMap<>();
    
    static {
        PACKAGE_MOVES.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        PACKAGE_MOVES.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        
        CLASS_RENAMES.put("RandomText", "Randomized");
        CLASS_RENAMES.put("SplitText", "Split"); 
        CLASS_RENAMES.put("TrimmedText", "Trimmed");
        CLASS_RENAMES.put("JoinedText", "Joined");
        CLASS_RENAMES.put("CheckedScalar", "Checked");
        CLASS_RENAMES.put("UncheckedScalar", "Unchecked");
        CLASS_RENAMES.put("IoCheckedScalar", "IoChecked");
        CLASS_RENAMES.put("StickyScalar", "Sticky");
        CLASS_RENAMES.put("SolidScalar", "Solid");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            int changes = applyTransformations(model);
            
            System.out.println("Applied " + changes + " transformations");
            
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int applyTransformations(CtModel model) {
        int changeCount = 0;
        
        for (CtType<?> type : model.getAllTypes()) {
            changeCount += fixTypeReferences(type);
        }
        
        return changeCount;
    }
    
    private static int fixTypeReferences(CtType<?> type) {
        int changes = 0;
        
        List<CtTypeReference<?>> typeRefs = type.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                return true;
            }
        });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> entry : PACKAGE_MOVES.entrySet()) {
                if (qualifiedName.equals(entry.getKey())) {
                    CtTypeReference<?> newRef = type.getFactory().createReference(entry.getValue());
                    typeRef.replace(newRef);
                    changes++;
                    break;
                }
            }
            
            for (Map.Entry<String, String> entry : CLASS_RENAMES.entrySet()) {
                if (qualifiedName.endsWith("." + entry.getKey())) {
                    String newQualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf(".")) + "." + entry.getValue();
                    CtTypeReference<?> newRef = type.getFactory().createReference(newQualifiedName);
                    typeRef.replace(newRef);
                    changes++;
                    break;
                }
            }
        }
        
        return changes;
    }
}