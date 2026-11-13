package github.chains;

import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        final Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCopyResources(false);

        launcher.addProcessor(new AbstractProcessor<CtCompilationUnit>() {
            @Override
            public void process(CtCompilationUnit compilationUnit) {
                final List<CtImport> imports = new ArrayList<>(compilationUnit.getImports());
                boolean changed = false;
                for (int i = 0; i < imports.size(); i++) {
                    final CtImport ctImport = imports.get(i);
                    final CtReference reference = ctImport.getReference();
                    if (reference instanceof CtTypeReference<?> typeReference) {
                        final String qualifiedName = typeReference.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX)) {
                            imports.set(i, compilationUnit.getFactory().createImport(
                                    compilationUnit.getFactory().createReference(rewriteQualifiedName(qualifiedName))));
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    compilationUnit.setImports(imports);
                }
            }
        });

        launcher.run();
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
    }
}
