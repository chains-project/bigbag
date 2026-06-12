package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String OLD_TYPE = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_TYPE = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-directory> <output-directory>");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(input.toString());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        List<CtTypeReference<?>> refs = launcher.getModel().getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> ref : refs) {
            rewriteType(ref, factory);
        }

        List<CtImport> imports = launcher.getModel().getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference<?>) {
                CtTypeReference<?> ref = (CtTypeReference<?>) ctImport.getReference();
                if (OLD_TYPE.equals(ref.getQualifiedName())) {
                    CtTypeReference<?> replacement = ref.clone();
                    replacement.setPackage(factory.createPackageReference().setSimpleName("com.github.javaparser.printer.configuration"));
                    ctImport.setReference(replacement);
                }
            }
        }

        launcher.getEnvironment().setSourceOutputDirectory(output.toFile());
        launcher.prettyprint();
    }

    private static void rewriteType(CtTypeReference<?> ref, Factory factory) {
        if (ref == null) {
            return;
        }
        if (OLD_TYPE.equals(ref.getQualifiedName())) {
            ref.setPackage(factory.createPackageReference().setSimpleName("com.github.javaparser.printer.configuration"));
        }
    }
}
