package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String KD_TREE = "org.tinspin.index.kdtree.KDTree";
    private static final String POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String POINT_MAP = "org.tinspin.index.PointMap";
    private static final String POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String QUERY_OLD = "query1NN";
    private static final String QUERY_NEW = "query1nn";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length >= 2 ? Paths.get(args[1]).toAbsolutePath().normalize() : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setAutoImports(false);
        env.setCommentEnabled(true);
        env.setIgnoreSyntaxErrors(true);
        env.setShouldCompile(false);
        env.setSourceOutputDirectory(output.toFile());

        addCandidateSources(launcher, input);
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        TinspinTypeRewriteProcessor typeProcessor = new TinspinTypeRewriteProcessor(factory);
        TinspinInvocationRewriteProcessor invocationProcessor = new TinspinInvocationRewriteProcessor();
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeProcessor::process);
        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(invocation -> invocationProcessor.process((CtInvocation<?>) invocation));
        launcher.prettyprint();
    }

    private static void addCandidateSources(Launcher launcher, Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(Main::containsTinspinMarker)
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan sources under: " + root, e);
        }
    }

    private static boolean containsTinspinMarker(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.contains("org.tinspin") || source.contains(QUERY_OLD) || source.contains("query1nn") || source.contains("PointEntryDist") || source.contains("PointIndex");
        } catch (IOException e) {
            return false;
        }
    }

    private static final class TinspinTypeRewriteProcessor {
        private final Factory factory;

        private TinspinTypeRewriteProcessor(Factory factory) {
            this.factory = factory;
        }

        public void process(CtTypeReference<?> reference) {
            if (reference == null || reference.getQualifiedName() == null) {
                return;
            }

            switch (reference.getQualifiedName()) {
                case POINT_INDEX -> reference.replace(factory.createReference(POINT_MAP));
                case POINT_DISTANCE_FUNCTION -> reference.replace(factory.createReference(POINT_DISTANCE));
                case POINT_ENTRY_DIST -> reference.replace(factory.createReference(POINT_ENTRY_KNN));
                default -> {
                }
            }
        }
    }

    private static final class TinspinInvocationRewriteProcessor {
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || executable.getSimpleName() == null) {
                return;
            }

            rewriteQueryName(invocation, executable);
            rewriteKdtreeCreate(invocation, executable);
        }

        private void rewriteQueryName(CtInvocation<?> invocation, CtExecutableReference<?> executable) {
            if (!Objects.equals(executable.getSimpleName(), QUERY_OLD)) {
                return;
            }
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String qn = declaringType.getQualifiedName();
                if (qn != null && qn.startsWith("org.tinspin.index")) {
                    executable.setSimpleName(QUERY_NEW);
                    if (invocation.getExecutable() != null) {
                        invocation.getExecutable().setSimpleName(QUERY_NEW);
                    }
                }
            }
        }

        private void rewriteKdtreeCreate(CtInvocation<?> invocation, CtExecutableReference<?> executable) {
            if (!Objects.equals(executable.getSimpleName(), "create")) {
                return;
            }
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType == null || !KD_TREE.equals(declaringType.getQualifiedName())) {
                return;
            }
            if (invocation.getArguments().size() == 2) {
                invocation.getArguments().remove(1);
            }
        }
    }
}
