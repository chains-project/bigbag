package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Path inputDir = Paths.get(args[0]).toAbsolutePath();
        final Path outputDir = args.length > 1 ? Paths.get(args[1]).toAbsolutePath() : inputDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        final CtTypeReference<?> replacement = factory.Type().createReference(NEW_TYPE);

        final List<CtTypeReference<?>> references = new ArrayList<>(launcher.getModel()
                .getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                reference.replace(replacement.clone());
            }
        }

        final List<CtCompilationUnit> compilationUnits = launcher.getModel()
                .getElements(new TypeFilter<>(CtCompilationUnit.class));
        for (CtCompilationUnit compilationUnit : compilationUnits) {
            final List<?> imports = new ArrayList<>(compilationUnit.getImports());
            imports.removeIf(importObject -> importObject instanceof spoon.reflect.declaration.CtImport
                    && isOldZipImport((spoon.reflect.declaration.CtImport) importObject));
            compilationUnit.setImports((List) imports);
        }

        launcher.prettyprint();
    }

    private static boolean isOldZipImport(spoon.reflect.declaration.CtImport ctImport) {
        if (ctImport == null || !(ctImport.getReference() instanceof CtTypeReference)) {
            return false;
        }
        return OLD_TYPE.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName());
    }
}
