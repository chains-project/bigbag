package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.mockito.runners.MockitoJUnitRunner";
    private static final String NEW_TYPE = "org.mockito.junit.MockitoJUnitRunner";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        CtTypeReference<?> replacement =
            launcher.getFactory().Type().createReference(NEW_TYPE);

        for (CtCompilationUnit compilationUnit : launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            compilationUnit.getImports().removeIf(ctImport ->
                ctImport.getReference() instanceof CtTypeReference<?> typeReference
                    && OLD_TYPE.equals(typeReference.getQualifiedName()));
        }

        for (CtTypeReference<?> typeReference :
            launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(replacement.clone());
            }
        }

        launcher.prettyprint();
    }
}
