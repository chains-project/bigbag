package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class Main {

    private static final String OLD_TYPE = "org.cactoos.map.MapEntry";

    private static final String NEW_TYPE = "java.util.AbstractMap.SimpleEntry";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-dir> <output-dir>");
        }
        final Launcher launcher = new Launcher();
        try {
            Files.walk(Path.of(args[0]))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to read source tree", err);
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.buildModel();
        final CtTypeReference<?> newtype = launcher.getFactory().Type().createReference(NEW_TYPE);
        final var calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (final CtConstructorCall<?> call : calls) {
            final CtTypeReference<?> type = call.getType();
            if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                call.setType(newtype);
            }
        }
        final var imports = launcher.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (final CtImport imp : imports) {
            final CtReference reference = imp.getReference();
            if (reference instanceof CtTypeReference
                && OLD_TYPE.equals(((CtTypeReference<?>) reference).getQualifiedName())) {
                imp.delete();
            }
        }
        launcher.setSourceOutputDirectory(args[1]);
        launcher.prettyprint();
    }
}
