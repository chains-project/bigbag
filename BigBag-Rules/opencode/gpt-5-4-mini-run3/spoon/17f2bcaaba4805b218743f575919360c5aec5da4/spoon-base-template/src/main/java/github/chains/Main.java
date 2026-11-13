package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Set<String> POINT_INDEX_TYPES = new HashSet<>(Arrays.asList(
            "org.tinspin.index.PointIndex",
            "org.tinspin.index.PointIndexMM",
            "org.tinspin.index.kdtree.KDTree",
            "org.tinspin.index.covertree.CoverTree",
            "org.tinspin.index.PointIndexWrapper",
            "org.tinspin.index.PointIndexMMWrapper"));

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least one argument: <sourceDir> [outputDir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-spoon-out");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            rewriteInvocation(invocation);
        }

        launcher.prettyprint();
    }

    private static void rewriteInvocation(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        String methodName = executable.getSimpleName();

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType != null) {
            String qn = declaringType.getQualifiedName();
            if ("org.junit.Assert".equals(qn)) {
                executable.setDeclaringType(invocation.getFactory().createReference("org.junit.jupiter.api.Assertions"));
            } else if ("org.junit.Assume".equals(qn)) {
                executable.setDeclaringType(invocation.getFactory().createReference("org.junit.jupiter.api.Assumptions"));
            }
        }

        if ("nnQuery".equals(methodName) && invocation.getArguments().size() == 1) {
            executable.setSimpleName("query1NN");
            return;
        }

        if ("knnQuery".equals(methodName) && invocation.getArguments().size() == 2) {
            executable.setSimpleName("queryKNN");
            return;
        }

        if ("query".equals(methodName) && invocation.getArguments().size() == 1 && isTinspinPointIndex(invocation.getTarget())) {
            executable.setSimpleName("queryExact");
        }
    }

    private static boolean isTinspinPointIndex(CtExpression<?> targetExpression) {
        if (targetExpression == null) {
            return false;
        }
        CtTypeReference<?> type = targetExpression.getType();
        if (type == null) {
            return false;
        }
        return isTinspinPointIndex(type);
    }

    private static boolean isTinspinPointIndex(CtTypeReference<?> type) {
        String qualifiedName = type.getQualifiedName();
        if (POINT_INDEX_TYPES.contains(qualifiedName)) {
            return true;
        }
        return qualifiedName != null && qualifiedName.startsWith("org.tinspin.index.");
    }
}
