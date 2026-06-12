package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    private static final String KD_TREE = "org.tinspin.index.kdtree.KDTree";
    private static final String COVER_TREE = "org.tinspin.index.covertree.CoverTree";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : new File(inputDir.getParentFile(), inputDir.getName() + "-spooned");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        transformTypes(launcher);
        transformInvocations(launcher);

        launcher.prettyprint();
    }

    private static void transformTypes(Launcher launcher) {
        List<CtTypeReference<?>> refs = new ArrayList<>(launcher.getModel().getElements(e -> e instanceof CtTypeReference));
        for (CtElement element : refs) {
            CtTypeReference<?> typeRef = (CtTypeReference<?>) element;
            if (OLD_POINT_DISTANCE_FUNCTION.equals(typeRef.getQualifiedName())) {
                typeRef.replace(launcher.getFactory().Type().createReference(NEW_POINT_DISTANCE));
            } else if (OLD_POINT_ENTRY_DIST.equals(typeRef.getQualifiedName())) {
                typeRef.replace(launcher.getFactory().Type().createReference(NEW_POINT_ENTRY_KNN));
            } else if (OLD_POINT_INDEX.equals(typeRef.getQualifiedName())) {
                typeRef.replace(launcher.getFactory().Type().createReference(NEW_POINT_MAP));
            }
        }
    }

    private static void transformInvocations(Launcher launcher) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(e -> e instanceof CtInvocation));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
            String declaringTypeName = declaringType == null ? null : declaringType.getQualifiedName();

            if (isTinSpinQuery1NN(invocation, declaringTypeName)) {
                executable.setSimpleName("query1nn");
            }

            if (isKdTreeCreateWithDistance(invocation, declaringTypeName)) {
                if (invocation.getArguments().size() == 2) {
                    invocation.getArguments().remove(1);
                }
            }

            if (isPointIndexQuery1NN(invocation, declaringTypeName)) {
                executable.setSimpleName("query1nn");
            }
        }
    }

    private static boolean isTinSpinQuery1NN(CtInvocation<?> invocation, String declaringTypeName) {
        if (declaringTypeName == null) {
            return false;
        }
        String name = invocation.getExecutable() == null ? null : invocation.getExecutable().getSimpleName();
        return "query1NN".equals(name) && (KD_TREE.equals(declaringTypeName) || COVER_TREE.equals(declaringTypeName) || declaringTypeName.startsWith("org.tinspin.index."));
    }

    private static boolean isPointIndexQuery1NN(CtInvocation<?> invocation, String declaringTypeName) {
        return isTinSpinQuery1NN(invocation, declaringTypeName);
    }

    private static boolean isKdTreeCreateWithDistance(CtInvocation<?> invocation, String declaringTypeName) {
        if (!KD_TREE.equals(declaringTypeName)) {
            return false;
        }
        CtExecutableReference<?> executable = invocation.getExecutable();
        return executable != null && "create".equals(executable.getSimpleName()) && invocation.getArguments().size() == 2;
    }
}
