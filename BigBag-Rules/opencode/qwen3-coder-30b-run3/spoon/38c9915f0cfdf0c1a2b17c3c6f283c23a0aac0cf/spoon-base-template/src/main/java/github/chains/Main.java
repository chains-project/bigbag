package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("/tmp/generated");
        launcher.getEnvironment().setComplianceLevel(8);
        
        Factory factory = launcher.getFactory();
        
        // Transform all the problematic patterns
        transformPatterns(factory);
        
        // Build model and write changes
        launcher.buildModel();
        launcher.setSourceOutputDirectory("/tmp/generated");
        launcher.process();
    }
    
    /**
     * Generic transformation method to fix cactoos API breaking changes
     * For now, just remove the problematic imports
     */
    private static void transformPatterns(Factory factory) {
        // Handle imports for deprecated classes
        handleDeprecatedImports(factory);
    }
    
    private static void handleDeprecatedImports(Factory factory) {
        // Remove deprecated import statements 
        List<CtImport> importsToRemove = new ArrayList<>();
        for (CtImport imp : factory.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (imp.getReference() != null) {
                String qualifiedName = imp.getReference().toString();
                if (qualifiedName.startsWith("org.cactoos.text.RandomText") ||
                    qualifiedName.startsWith("org.cactoos.collection.CollectionOf") ||
                    qualifiedName.startsWith("org.cactoos.text.SplitText") ||
                    qualifiedName.startsWith("org.cactoos.text.JoinedText") ||
                    qualifiedName.startsWith("org.cactoos.text.TrimmedText") ||
                    qualifiedName.startsWith("org.cactoos.scalar.CheckedScalar") ||
                    qualifiedName.startsWith("org.cactoos.scalar.UncheckedScalar") ||
                    qualifiedName.startsWith("org.cactoos.scalar.IoCheckedScalar") ||
                    qualifiedName.startsWith("org.cactoos.scalar.SolidScalar") ||
                    qualifiedName.startsWith("org.cactoos.scalar.StickyScalar")) {
                    importsToRemove.add(imp);
                }
            }
        }
        
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }
    }
}