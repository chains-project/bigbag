package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String KD_TREE = "org.tinspin.index.kdtree.KDTree";
    private static final String QUERY_OLD = "query1NN";
    private static final String QUERY_NEW = "query1nn";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);

        for (Path file : findCandidateFiles(Paths.get(args[0]))) {
            launcher.addInputResource(file.toString());
        }

        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        replaceType(launcher, OLD_POINT_INDEX, NEW_POINT_MAP);
        replaceType(launcher, OLD_POINT_DISTANCE_FUNCTION, NEW_POINT_DISTANCE);
        replaceType(launcher, OLD_POINT_ENTRY_DIST, NEW_POINT_ENTRY_KNN);

        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            renameNearestNeighborCall(invocation);
            dropLegacyKdTreeDistanceArgument(invocation);
        }

        launcher.prettyprint();
    }

    private static List<Path> findCandidateFiles(Path sourceRoot) {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .filter(Main::containsLegacyTinspinApi)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean containsLegacyTinspinApi(Path file) {
        try {
            String content = Files.readString(file);
            return content.contains(OLD_POINT_INDEX)
                    || content.contains(OLD_POINT_DISTANCE_FUNCTION)
                    || content.contains(OLD_POINT_ENTRY_DIST)
                    || content.contains(QUERY_OLD)
                    || content.contains("query1nn")
                    || content.contains("KDTree.create(2, ");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void replaceType(Launcher launcher, String oldQualifiedName, String newQualifiedName) {
        List<CtTypeReference<?>> refs = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> ref : refs) {
            if (oldQualifiedName.equals(ref.getQualifiedName())) {
                CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(newQualifiedName);
                replacement.setActualTypeArguments(new ArrayList<>(ref.getActualTypeArguments()));
                ref.replace(replacement);
            }
        }
    }

    private static void renameNearestNeighborCall(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable != null && QUERY_OLD.equals(executable.getSimpleName())) {
            executable.setSimpleName(QUERY_NEW);
        }
    }

    private static void dropLegacyKdTreeDistanceArgument(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || executable.getDeclaringType() == null) {
            return;
        }
        if (!KD_TREE.equals(executable.getDeclaringType().getQualifiedName())) {
            return;
        }
        if (!"create".equals(executable.getSimpleName())) {
            return;
        }
        if (invocation.getArguments().size() != 2) {
            return;
        }
        invocation.getArguments().remove(1);
    }
}
