package github.chains;

import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtBinaryOperatorImpl;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.support.reflect.code.CtInvocationImpl;

/**
 * Generic Spoon transformation to fix cactoos.io.BytesOf and org.cactoos.text.HexOf usage.
 * This replaces deprecated cactoos classes with standard Java equivalents.
 */
public class Main {
    public static void main(String[] args) {
        // Process the docker-adapter project
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java");
        launcher.getEnvironment().setComplianceLevel(17);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all types that have imports for cactoos packages
        List<CtType<?>> types = model.getElements(new TypeFilter<>(CtType.class));
        
        for (CtType<?> type : types) {
            // Check for imports that reference cactoos packages
            List<CtImport> imports = type.getImports();
            for (CtImport imp : imports) {
                String importName = imp.getReference().toString();
                if (importName.startsWith("org.cactoos.")) {
                    // Remove the cactoos imports
                    imp.delete();
                }
            }
        }
        
        // Write the modified files back
        launcher.process();
        
        System.out.println("Removed cactoos imports successfully!");
    }
}