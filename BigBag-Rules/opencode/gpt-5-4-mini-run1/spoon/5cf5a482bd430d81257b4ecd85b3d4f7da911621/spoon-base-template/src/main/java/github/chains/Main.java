package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_PREFIX = "javax.mvc";
    private static final String NEW_PREFIX = "jakarta.mvc";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.buildModel();

        transform(launcher.getModel());

        if (args.length > 1) {
            launcher.setSourceOutputDirectory(args[1]);
            launcher.prettyprint();
        }
    }

    static void transform(CtModel model) {
        model.getElements(new TypeFilter<>(CtCompilationUnit.class)).forEach(Main::rewriteCompilationUnitImports);
        model.getElements(new TypeFilter<>(CtImport.class)).forEach(Main::rewriteImport);
        model.getElements(new TypeFilter<>(CtTypeReference.class)).forEach(Main::rewriteTypeReference);
    }

    private static void rewriteCompilationUnitImports(CtCompilationUnit compilationUnit) {
        var rewrittenImports = new java.util.ArrayList<CtImport>();
        for (CtImport ctImport : compilationUnit.getImports()) {
            CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference<?> typeReference) {
                String qualifiedName = typeReference.getQualifiedName();
                if (qualifiedName != null && isOldNamespace(qualifiedName)) {
                    rewrittenImports.add(ctImport.getFactory().Type().createImport(ctImport.getFactory().Type().createReference(toJakarta(qualifiedName))));
                    continue;
                }
            }
            rewrittenImports.add(ctImport);
        }

        compilationUnit.getImports().clear();
        compilationUnit.getImports().addAll(rewrittenImports);
    }

    private static void rewriteImport(CtImport ctImport) {
        CtReference reference = ctImport.getReference();
        if (reference == null) {
            return;
        }

        if (reference instanceof CtTypeReference<?> typeReference) {
            String qualifiedName = typeReference.getQualifiedName();
            if (qualifiedName != null && isOldNamespace(qualifiedName)) {
                ctImport.setReference(ctImport.getFactory().Type().createReference(toJakarta(qualifiedName)));
            }
        }
    }

    private static void rewriteTypeReference(CtTypeReference<?> typeReference) {
        String qualifiedName = typeReference.getQualifiedName();
        if (qualifiedName == null || !isOldNamespace(qualifiedName)) {
            return;
        }

        typeReference.replace(typeReference.getFactory().Type().createReference(toJakarta(qualifiedName)));
    }

    private static String toJakarta(String qualifiedName) {
        return qualifiedName.equals(OLD_PREFIX) ? NEW_PREFIX : qualifiedName.replaceFirst("^javax\\.mvc", NEW_PREFIX);
    }

    private static boolean isOldNamespace(String qualifiedName) {
        return qualifiedName.equals(OLD_PREFIX) || qualifiedName.startsWith(OLD_PREFIX + ".");
    }
}
