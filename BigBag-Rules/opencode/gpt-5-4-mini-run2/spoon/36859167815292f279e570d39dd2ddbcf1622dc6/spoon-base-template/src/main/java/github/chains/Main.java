package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.MavenLauncher;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a Maven source directory path");
        }

        Path sourceDirectory = Paths.get(args[0]).toAbsolutePath().normalize();
        Path projectRoot = locateProjectRoot(sourceDirectory);

        Launcher launcher = new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(sourceDirectory.toFile());
        launcher.buildModel();

        int updated = 0;
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isLegacyFopFactoryNewInstance(invocation)) {
                invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                        "new org.apache.fop.apps.FopFactoryBuilder(new java.io.File(\".\").toURI()).build()"));
                updated++;
            }
        }

        launcher.prettyprint();
        System.out.println("Updated " + updated + " call site(s).");
    }

    private static boolean isLegacyFopFactoryNewInstance(CtInvocation<?> invocation) {
        if (!"newInstance".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        return declaringType != null
                && "org.apache.fop.apps.FopFactory".equals(declaringType.getQualifiedName())
                && invocation.getArguments().isEmpty();
    }

    private static Path locateProjectRoot(Path sourceDirectory) {
        Path current = sourceDirectory;
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return sourceDirectory;
    }
}
