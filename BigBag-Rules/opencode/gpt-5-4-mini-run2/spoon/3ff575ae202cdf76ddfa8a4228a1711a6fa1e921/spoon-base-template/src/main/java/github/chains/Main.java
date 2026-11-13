package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_OWNER = "org.codehaus.plexus.PlexusContainer";
    private static final String NEW_OWNER = "org.codehaus.plexus.MutablePlexusContainer";
    private static final String METHOD_NAME = "getLoggerManager";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length >= 2 ? Paths.get(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.setSourceOutputDirectory(outputDir.toFile());

        for (Path sourceRoot : resolveSourceRoots(inputDir)) {
            launcher.addInputResource(sourceRoot.toString());
        }

        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || !METHOD_NAME.equals(executable.getSimpleName())) {
                continue;
            }

            CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
            if (targetType == null || !OLD_OWNER.equals(targetType.getQualifiedName())) {
                continue;
            }

            if (invocation.getTarget() instanceof CtTypeAccess) {
                continue;
            }

            invocation.replace(
                launcher.getFactory().Code().createCodeSnippetExpression(
                    "((" + NEW_OWNER + ") " + invocation.getTarget().toString() + ")." + METHOD_NAME + "()"
                )
            );
        }

        launcher.prettyprint();
    }

    private static List<Path> resolveSourceRoots(Path inputDir) {
        List<Path> sourceRoots = new ArrayList<>();
        if (Files.isDirectory(inputDir.resolve("src/main/java"))) {
            sourceRoots.add(inputDir.resolve("src/main/java"));
        }
        if (Files.isDirectory(inputDir.resolve("src/test/java"))) {
            sourceRoots.add(inputDir.resolve("src/test/java"));
        }
        if (sourceRoots.isEmpty()) {
            sourceRoots.add(inputDir);
        }
        return sourceRoots;
    }
}
