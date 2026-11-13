package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.mockito.runners.MockitoJUnitRunner";
    private static final String NEW_TYPE = "org.mockito.junit.MockitoJUnitRunner";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel();

        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() instanceof CtTypeReference<?> ref
                && OLD_TYPE.equals(ref.getQualifiedName())) {
                ctImport.setReference(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        }

        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                reference.replace(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        }

        launcher.prettyprint();
    }
}
