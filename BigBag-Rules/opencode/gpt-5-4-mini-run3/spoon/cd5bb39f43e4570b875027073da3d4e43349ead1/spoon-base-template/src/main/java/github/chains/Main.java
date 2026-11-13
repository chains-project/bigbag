package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_ABSTRACT_TYPE = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String NEW_CONCRETE_TYPE = "org.codehaus.plexus.configuration.DefaultPlexusConfiguration";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Path.of(args[0]);
        Path outputDir = args.length == 2 ? Path.of(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        addJavaSources(launcher, sourceDir);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        CtModel model = launcher.getModel();
        replaceTypeReferences(model);
        replaceConstructorCalls(model);

        launcher.prettyprint();
    }

    private static void replaceTypeReferences(CtModel model) {
        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(typeReference.getFactory().createReference(NEW_ABSTRACT_TYPE));
            }
        }
    }

    private static void replaceConstructorCalls(CtModel model) {
        for (CtConstructorCall<?> constructorCall : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> type = constructorCall.getType();
            if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                constructorCall.setType(constructorCall.getFactory().createReference(NEW_CONCRETE_TYPE));
            }
        }
    }

    private static void addJavaSources(Launcher launcher, Path sourceDir) {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/src/it/"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan source directory " + sourceDir, e);
        }
    }
}
