package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String TARGET_METHOD = "addEnabledLanguages";
    private static final String TARGET_OWNER =
            "org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration.AbstractBuilder";
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected an input source directory path");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir.resolveSibling("spooned-output");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isTargetInvocation(invocation) || invocation.getTarget() == null) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            invocation.replace(target.clone());
        }

        launcher.prettyprint();
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        if (!TARGET_METHOD.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        return declaringType == null || TARGET_OWNER.equals(declaringType.getQualifiedName());
    }
}
