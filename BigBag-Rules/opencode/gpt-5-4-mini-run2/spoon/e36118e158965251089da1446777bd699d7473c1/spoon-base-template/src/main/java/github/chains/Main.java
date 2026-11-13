package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation for replacing removed MapEntry constructors.
 */
public final class Main {

    private static final String OLD_TYPE = "org.cactoos.map.MapEntry";

    private static final String NEW_TYPE = "java.util.AbstractMap.SimpleEntry";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        addJavaSources(launcher, input);
        launcher.buildModel();
        transform(launcher.getFactory());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.prettyprint();
        removeLegacyImports(output, OLD_TYPE);
    }

    private static void addJavaSources(final Launcher launcher, final Path input) {
        try (Stream<Path> paths = Files.walk(input)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void transform(final Factory factory) {
        final CtModel model = factory.getModel();
        model.getElements(new TypeFilter<>(CtImport.class)).stream()
            .filter(imp -> imp.getReference() instanceof CtTypeReference)
            .filter(imp -> OLD_TYPE.equals(((CtTypeReference<?>) imp.getReference()).getQualifiedName()))
            .forEach(imp -> {
                if (imp.getPosition() != null && imp.getPosition().getCompilationUnit() != null) {
                    final Collection<CtImport> imports = new ArrayList<>(
                        imp.getPosition().getCompilationUnit().getImports()
                    );
                    imports.remove(imp);
                    imp.getPosition().getCompilationUnit().setImports(imports);
                }
                imp.delete();
            });
        model.getElements(new TypeFilter<>(CtConstructorCall.class)).stream()
            .filter(call -> call.getType() != null)
            .filter(call -> OLD_TYPE.equals(call.getType().getQualifiedName()))
            .forEach(call -> replaceWithSimpleEntry(factory, call));
    }

    private static void replaceWithSimpleEntry(
        final Factory factory,
        final CtConstructorCall<?> oldCall
    ) {
        final CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
            factory.Type().createReference(NEW_TYPE),
            oldCall.getArguments().toArray(CtExpression[]::new)
        );
        replacement.setComments(oldCall.getComments());
        oldCall.replace(replacement);
    }

    private static void removeLegacyImports(final Path output, final String typeName) {
        final String legacy = "import " + typeName + ";";
        try (Stream<Path> paths = Files.walk(output)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        final String text = Files.readString(path);
                        if (text.contains(legacy)) {
                            Files.writeString(path, text.replace(legacy + System.lineSeparator(), ""));
                        }
                    } catch (IOException err) {
                        throw new IllegalStateException(err);
                    }
                });
        } catch (IOException err) {
            throw new IllegalStateException(err);
        }
    }
}
