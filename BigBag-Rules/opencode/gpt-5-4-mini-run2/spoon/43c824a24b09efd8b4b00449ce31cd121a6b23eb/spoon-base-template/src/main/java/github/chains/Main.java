package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_TYPE = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_METHOD = "getJsonPath";
    private static final String NEW_METHOD = "getPath";

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-project-or-source-root> [output-project-root]");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length == 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : inputRoot;

        for (Path sourceRoot : discoverSourceRoots(inputRoot)) {
            transformSourceRoot(inputRoot, outputRoot, sourceRoot);
        }
    }

    private static List<Path> discoverSourceRoots(Path inputRoot) throws IOException {
        if (looksLikeSourceRoot(inputRoot)) {
            return List.of(inputRoot);
        }

        List<Path> roots = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(inputRoot)) {
            paths.filter(Files::isDirectory)
                .filter(Main::looksLikeSourceRoot)
                .forEach(roots::add);
        }
        return roots;
    }

    private static boolean looksLikeSourceRoot(Path path) {
        return path.getFileName() != null
            && "java".equals(path.getFileName().toString())
            && Files.exists(path.resolve("."));
    }

    private static void transformSourceRoot(Path inputRoot, Path outputRoot, Path sourceRoot) throws IOException {
        Path sourceOutput = inputRoot.equals(outputRoot) ? sourceRoot : outputRoot;
        Files.createDirectories(sourceOutput);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setSourceOutputDirectory(sourceOutput.toFile());
        // Keep no-classpath mode so the transformation can rewrite sources
        // even when the upgraded dependency has removed old types.
        launcher.addInputResource(sourceRoot.toString());
        launcher.addProcessor(new LegacyInvocationProcessor());
        launcher.addProcessor(new LegacyImportProcessor());
        launcher.addProcessor(new LegacyTypeReferenceProcessor());
        launcher.run();
    }

    private static boolean isLegacyType(CtTypeReference<?> typeReference) {
        return typeReference != null && OLD_TYPE.equals(typeReference.getQualifiedName());
    }

    private static final class LegacyImportProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport ctImport) {
            if (ctImport.getReference() instanceof CtTypeReference<?> typeReference && isLegacyType(typeReference)) {
                ctImport.replace(getFactory().createImport(getFactory().Type().createReference(NEW_TYPE)));
            }
        }
    }

    private static final class LegacyInvocationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (!OLD_METHOD.equals(executable.getSimpleName())) {
                return;
            }

            CtTypeReference<?> declaringType = executable.getDeclaringType();
            CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
            if (isLegacyType(declaringType) || isLegacyType(targetType)) {
                executable.setSimpleName(NEW_METHOD);
                executable.setDeclaringType(getFactory().Type().createReference(NEW_TYPE));
            }
        }
    }

    private static final class LegacyTypeReferenceProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeReference) {
            if (isLegacyType(typeReference)) {
                typeReference.replace(getFactory().Type().createReference(NEW_TYPE));
            }
        }
    }
}
