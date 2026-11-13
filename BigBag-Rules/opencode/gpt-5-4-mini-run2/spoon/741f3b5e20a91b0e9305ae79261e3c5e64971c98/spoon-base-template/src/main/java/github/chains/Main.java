package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OBJECT_MAPPER_FQN = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String READ_VALUE = "readValue";
    private static final String READ_TREE = "readTree";
    private static final String TREE_TO_VALUE = "treeToValue";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-transformed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(inputDir);
        launcher.buildModel();

        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!isObjectMapperReadValueWithClassArgument(invocation)) {
                continue;
            }

            String receiver = invocation.getTarget().clone().toString();
            String jsonSource = invocation.getArguments().get(0).clone().toString();
            String targetType = invocation.getArguments().get(1).clone().toString();
            String replacement = receiver + "." + TREE_TO_VALUE + "(" + receiver + "." + READ_TREE + "(" + jsonSource + "), " + targetType + ")";
            invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression(replacement));
        }

        launcher.prettyprint();
    }

    private static boolean isObjectMapperReadValueWithClassArgument(CtInvocation<?> invocation) {
        if (!READ_VALUE.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        if (declaringType == null || !OBJECT_MAPPER_FQN.equals(declaringType.getQualifiedName())) {
            return false;
        }

        return invocation.getArguments().size() == 2
                && invocation.getArguments().get(1).getType() != null
                && "java.lang.Class".equals(invocation.getArguments().get(1).getType().getQualifiedName());
    }
}
