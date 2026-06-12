package github.chains;

import java.io.File;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String TARGET_DECLARING_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration$Builder";
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }

        File sourceDirectory = new File(args[0]);
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory.getAbsolutePath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(sourceDirectory);
        launcher.buildModel();

        rewriteMatchingInvocations(launcher.getModel().getAllTypes());

        launcher.prettyprint();
    }

    private static void rewriteMatchingInvocations(Collection<CtType<?>> types) {
        for (CtType<?> type : types) {
            for (CtInvocation<?> invocation : type.getElements(new TypeFilter<>(CtInvocation.class))) {
                if (!isTargetInvocation(invocation)) {
                    continue;
                }

                List<CtExpression<?>> arguments = invocation.getArguments();
                if (arguments.size() != 1) {
                    continue;
                }

                CtExpression<?> argument = arguments.get(0);
                CtExpression<?> replacement = toArrayArgument(invocation, argument);
                if (replacement == null) {
                    continue;
                }

                invocation.setArguments(Collections.singletonList(replacement));
            }
        }
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null) {
            return false;
        }

        if (!TARGET_METHOD.equals(executable.getSimpleName())) {
            return false;
        }

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        return declaringType != null && TARGET_DECLARING_TYPE.equals(declaringType.getQualifiedName());
    }

    private static CtExpression<?> toArrayArgument(CtInvocation<?> invocation, CtExpression<?> argument) {
        CtTypeReference<?> parameterType = invocation.getExecutable().getParameters().get(0);
        if (parameterType == null || !parameterType.isArray()) {
            return null;
        }

        if (!(parameterType instanceof CtArrayTypeReference)) {
            return null;
        }

        String componentTypeName = ((CtArrayTypeReference<?>) parameterType).getComponentType().getQualifiedName();
        String snippet = argument + ".toArray(new " + componentTypeName + "[0])";
        return invocation.getFactory().Code().createCodeSnippetExpression(snippet);
    }
}
