package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root> [output-root]");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceRoot.resolveSibling(sourceRoot.getFileName() + "-spoon-out");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceRoot.toString());
        launcher.setSourceOutputDirectory(outputRoot.toFile());
        launcher.buildModel();

        rewriteClientHelperConstructors(launcher);
        rewriteStringUtilsAllBlank(launcher);

        launcher.prettyprint();
    }

    private static void rewriteClientHelperConstructors(Launcher launcher) {
        List<CtConstructorCall> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !"org.jenkinsci.plugins.p4.client.ClientHelper".equals(type.getQualifiedName())) {
                continue;
            }
            if (call.getArguments().size() != 4) {
                continue;
            }

            CtExpression<?> credentialId = call.getArguments().get(0).clone();
            CtExpression<?> listener = call.getArguments().get(1).clone();
            CtExpression<?> clientName = call.getArguments().get(2).clone();
            CtExpression<?> charset = call.getArguments().get(3).clone();

            CtCodeSnippetExpression<?> rewritten = call.getFactory().Code().createCodeSnippetExpression(
                    "new org.jenkinsci.plugins.p4.client.ClientHelper(" +
                            "org.jenkinsci.plugins.p4.client.ConnectionHelper.findCredential(" + credentialId + "), " +
                            listener + ", " +
                            "new org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl(" + charset + ", false, " + clientName + "))"
            );
            call.replace(rewritten);
        }
    }

    private static void rewriteStringUtilsAllBlank(Launcher launcher) {
        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || !"isAllBlank".equals(executable.getSimpleName())) {
                continue;
            }
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType == null || !"org.apache.commons.lang3.StringUtils".equals(declaringType.getQualifiedName())) {
                continue;
            }

            List<CtExpression<?>> arguments = invocation.getArguments();
            CtCodeSnippetExpression<Boolean> replacement = null;
            for (CtExpression<?> argument : arguments) {
                CtCodeSnippetExpression<Boolean> blankCheck = invocation.getFactory().Code().createCodeSnippetExpression(
                        "org.apache.commons.lang3.StringUtils.isBlank(" + argument + ")"
                );
                if (replacement == null) {
                    replacement = blankCheck;
                } else {
                    replacement = invocation.getFactory().Code().createCodeSnippetExpression(
                            "(" + replacement + ") && (" + blankCheck + ")"
                    );
                }
            }

            invocation.replace(replacement != null ? replacement : invocation.getFactory().Code().createLiteral(true));
        }
    }
}
