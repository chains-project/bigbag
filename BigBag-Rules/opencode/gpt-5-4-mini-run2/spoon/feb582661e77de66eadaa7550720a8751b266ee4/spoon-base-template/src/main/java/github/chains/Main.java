package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_STRING_UTILS = "liquibase.util.StringUtils";
    private static final String NEW_STRING_UTIL = "liquibase.util.StringUtil";
    private static final String OLD_EXECUTOR_SERVICE = "liquibase.executor.ExecutorService";
    private static final String NEW_SCOPE = "liquibase.Scope";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least one argument: <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length >= 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir;

        Files.createDirectories(outputDir);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        int fixes = 0;
        fixes += replaceOldStringUtils(launcher);
        fixes += replaceOldExecutorService(launcher);

        launcher.prettyprint();
        System.out.println("Applied " + fixes + " transformation(s) to " + inputDir + " -> " + outputDir);
    }

    private static int replaceOldStringUtils(Launcher launcher) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        int fixes = 0;

        for (CtInvocation<?> invocation : invocations) {
            if (!"trimToNull".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType == null || !OLD_STRING_UTILS.equals(declaringType.getQualifiedName())) {
                continue;
            }

            if (invocation.getArguments().size() != 1) {
                continue;
            }

            String replacement = NEW_STRING_UTIL + ".trimToNull(" + invocation.getArguments().get(0) + ")";
            CtCodeSnippetExpression<?> snippet = launcher.getFactory().Code().createCodeSnippetExpression(replacement);
            invocation.replace(snippet);
            fixes++;
        }

        return fixes;
    }

    private static int replaceOldExecutorService(Launcher launcher) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        int fixes = 0;

        for (CtInvocation<?> invocation : invocations) {
            if (!"getInstance".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType == null || !OLD_EXECUTOR_SERVICE.equals(declaringType.getQualifiedName())) {
                continue;
            }

            if (!invocation.getArguments().isEmpty()) {
                continue;
            }

            String replacement = NEW_SCOPE + ".getCurrentScope().getSingleton(" + OLD_EXECUTOR_SERVICE + ".class)";
            CtCodeSnippetExpression<?> snippet = launcher.getFactory().Code().createCodeSnippetExpression(replacement);
            invocation.replace(snippet);
            fixes++;
        }

        return fixes;
    }
}
