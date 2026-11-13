package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        new Main().transform(new File(args[0]), new File(args[1]));
    }

    private void transform(File inputDir, File outputDir) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isFilterBuilderApply(invocation)) {
                invocation.getExecutable().setSimpleName("test");
            }
        }

        launcher.prettyprint();
    }

    private boolean isFilterBuilderApply(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !"apply".equals(executable.getSimpleName()) || invocation.getArguments().size() != 1) {
            return false;
        }

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType != null && "org.reflections.util.FilterBuilder".equals(declaringType.getQualifiedName())) {
            return true;
        }

        if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
            CtTypeReference<?> targetType = invocation.getTarget().getType();
            return "org.reflections.util.FilterBuilder".equals(targetType.getQualifiedName())
                    || "FilterBuilder".equals(targetType.getSimpleName());
        }

        return false;
    }
}
