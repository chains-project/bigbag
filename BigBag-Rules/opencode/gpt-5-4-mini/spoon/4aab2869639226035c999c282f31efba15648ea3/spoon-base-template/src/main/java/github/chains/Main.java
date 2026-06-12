package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_TYPE = "com.artipie.asto.factory.Storages";

    private static final String NEW_TYPE = "com.artipie.asto.factory.StoragesLoader";

    private Main() {
        // no-op
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final Path source = Paths.get(args[0]);
        final Path target = args.length == 2 ? Paths.get(args[1]) : source;
        final Launcher launcher = new Launcher();
        launcher.addInputResource(source.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.buildModel();
        apply(launcher);
        launcher.setSourceOutputDirectory(target.toFile());
        launcher.prettyprint();
    }

    private static void apply(final Launcher launcher) {
        launcher.getModel().getElements((CtElement element) -> true).forEach(element -> {
            if (element instanceof CtTypeReference<?> typeReference) {
                if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                    typeReference.replace(launcher.getFactory().Type().createReference(NEW_TYPE));
                }
            }
            if (element instanceof CtConstructorCall<?> constructorCall) {
                final CtTypeReference<?> type = constructorCall.getType();
                if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                    constructorCall.setType(launcher.getFactory().Type().createReference(NEW_TYPE));
                }
            }
        });

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            final CtCompilationUnit unit = type.getPosition().isValidPosition()
                    ? type.getPosition().getCompilationUnit() : null;
            if (unit == null) {
                continue;
            }
            unit.getImports().removeIf(imp -> {
                final String text = imp.getReference() == null ? imp.toString() : imp.getReference().toString();
                return text != null && text.contains(OLD_TYPE);
            });
        }
    }
}
