package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_COVERAGE_DATABASE = "org.pitest.coverage.CoverageDatabase";
    private static final String NEW_CODE_SOURCE = "org.pitest.classpath.CodeSource";
    private static final String LISTENER_ARGUMENTS = "org.pitest.mutationtest.ListenerArguments";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-dir> [output-dir]");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        if (args.length > 1) {
            launcher.setSourceOutputDirectory(args[1]);
        }

        launcher.buildModel();

        CtTypeReference<?> oldCoverageDb = launcher.getFactory().Type().createReference(OLD_COVERAGE_DATABASE);
        CtTypeReference<?> newCodeSource = launcher.getFactory().Type().createReference(NEW_CODE_SOURCE);

        rewriteCoverageDbDeclarations(launcher, oldCoverageDb, newCodeSource);
        rewriteListenerFactoryCalls(launcher, newCodeSource);

        launcher.prettyprint();
    }

    private static void rewriteCoverageDbDeclarations(Launcher launcher,
            CtTypeReference<?> oldCoverageDb,
            CtTypeReference<?> newCodeSource) {
        List<CtField<?>> fields = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtField.class)));
        for (CtField<?> field : fields) {
            if (sameType(field.getType(), oldCoverageDb) && typeUsesOldGetClassInfo(field.getDeclaringType())) {
                field.setType(newCodeSource);
            }
        }

        List<CtParameter<?>> parameters = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtParameter.class)));
        for (CtParameter<?> parameter : parameters) {
            if (sameType(parameter.getType(), oldCoverageDb) && typeUsesOldGetClassInfo(parameter.getParent(CtType.class))) {
                parameter.setType(newCodeSource);
            }
        }
    }

    private static void rewriteListenerFactoryCalls(Launcher launcher, CtTypeReference<?> newCodeSource) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!isListenerArgumentsGetCoverage(invocation)) {
                continue;
            }
            String receiver = invocation.getTarget() == null ? "" : invocation.getTarget().toString();
            String replacement = "new org.pitest.classpath.CodeSource(" + receiver + ".data().getMutationClassPaths())";
            CtExpression<?> snippet = launcher.getFactory().Code().createCodeSnippetExpression(replacement);
            invocation.replace(snippet);
        }
    }

    private static boolean typeUsesOldGetClassInfo(CtType<?> type) {
        if (type == null) {
            return false;
        }
        for (CtInvocation<?> invocation : type.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getExecutable() == null || invocation.getExecutable().getDeclaringType() == null) {
                continue;
            }
            if (!"getClassInfo".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (OLD_COVERAGE_DATABASE.equals(invocation.getExecutable().getDeclaringType().getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isListenerArgumentsGetCoverage(CtInvocation<?> invocation) {
        return invocation.getExecutable() != null
                && "getCoverage".equals(invocation.getExecutable().getSimpleName())
                && invocation.getExecutable().getDeclaringType() != null
                && LISTENER_ARGUMENTS.equals(invocation.getExecutable().getDeclaringType().getQualifiedName());
    }

    private static boolean sameType(CtTypeReference<?> left, CtTypeReference<?> right) {
        return left != null && right != null && left.getQualifiedName().equals(right.getQualifiedName());
    }
}
