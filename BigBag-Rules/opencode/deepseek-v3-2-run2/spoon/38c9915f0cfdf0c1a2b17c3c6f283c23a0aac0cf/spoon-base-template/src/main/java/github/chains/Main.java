package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    // Mapping of old fully-qualified class names to new fully-qualified class names
    private static final Map<String, String> CLASS_MAPPINGS = new HashMap<>();
    
    static {
        // Package migrations
        CLASS_MAPPINGS.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        
        // Scalar classes with name changes (remove "Scalar" suffix)
        CLASS_MAPPINGS.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        CLASS_MAPPINGS.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        CLASS_MAPPINGS.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        CLASS_MAPPINGS.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        CLASS_MAPPINGS.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        
        // Text classes with name changes (remove "Text" suffix)
        CLASS_MAPPINGS.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        CLASS_MAPPINGS.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
        CLASS_MAPPINGS.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        CLASS_MAPPINGS.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        
        // Collection classes (CollectionOf might need special handling)
        CLASS_MAPPINGS.put("org.cactoos.collection.CollectionOf", "org.cactoos.list.ListOf");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDirectory> <outputDirectory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src /path/to/output");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        String outputDirectory = args[1];

        System.out.println("Transforming Cactoos 0.xx to 0.55.0 API...");
        System.out.println("Source: " + sourceDirectory);
        System.out.println("Output: " + outputDirectory);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);

        try {
            CtModel model = launcher.buildModel();
            
            // Process all type references in the model
            processTypeReferences(model);
            
            // Generate the transformed code
            launcher.prettyprint();
            
            System.out.println("\nTransformation completed successfully!");
            System.out.println("Summary of API changes applied:");
            System.out.println("================================");
            
            for (Map.Entry<String, String> entry : CLASS_MAPPINGS.entrySet()) {
                if (!entry.getKey().equals(entry.getValue())) {
                    System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
                }
            }
            
            System.out.println("\nNote: This transformation handles:");
            System.out.println("1. Package migrations (e.g., LengthOf moved from iterable to scalar package)");
            System.out.println("2. Class name simplifications (removed 'Scalar' and 'Text' suffixes)");
            System.out.println("3. CollectionOf -> ListOf replacement");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processTypeReferences(CtModel model) {
        // Get all types in the model
        List<CtType<?>> types = model.getElements(new TypeFilter<CtType<?>>(CtType.class) {
            @Override
            public boolean matches(CtType<?> type) {
                return type instanceof CtClass;
            }
        });
        
        for (CtType<?> type : types) {
            // Process all type references within this type
            processTypeReferencesInElement(type);
        }
    }
    
    private static void processTypeReferencesInElement(CtType<?> element) {
        // Get all type references in this element
        List<CtTypeReference<?>> typeRefs = element.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                return typeRef != null && typeRef.getQualifiedName() != null;
            }
        });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String oldName = typeRef.getQualifiedName();
            if (CLASS_MAPPINGS.containsKey(oldName)) {
                String newName = CLASS_MAPPINGS.get(oldName);
                if (!oldName.equals(newName)) {
                    try {
                        // Create a new type reference with the updated name
                        CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newName);
                        typeRef.replace(newTypeRef);
                        System.out.println("Replaced reference: " + oldName + " -> " + newName + 
                                           " in " + element.getQualifiedName());
                    } catch (Exception e) {
                        System.err.println("Failed to replace " + oldName + " in " + 
                                         element.getQualifiedName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // Also process imports
        List<CtImport> imports = element.getElements(new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport ctImport) {
                return ctImport != null;
            }
        });
        
        for (CtImport ctImport : imports) {
            String importStr = ctImport.toString();
            for (Map.Entry<String, String> entry : CLASS_MAPPINGS.entrySet()) {
                String oldName = entry.getKey();
                String newName = entry.getValue();
                
                if (!oldName.equals(newName) && importStr.contains(oldName)) {
                    System.out.println("Import needs update: " + importStr + 
                                       " should be updated to use " + newName);
                    // Note: Spoon will auto-import the correct type based on replaced references
                }
            }
        }
    }
}