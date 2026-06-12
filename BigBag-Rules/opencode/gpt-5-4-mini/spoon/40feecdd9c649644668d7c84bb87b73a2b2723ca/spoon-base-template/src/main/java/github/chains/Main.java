package github.chains;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String DATE_TIME_FORMATTER = "org.joda.time.format.DateTimeFormatter";
    private static final String STRING_BUILDER = "java.lang.StringBuilder";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = new File(args.length > 1 ? args[1] : args[0]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.buildModel();

        Set<CtTry> candidates = new HashSet<>();
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isLegacyJodaPrintTo(invocation)) {
                continue;
            }

            CtTry tryStatement = invocation.getParent(new TypeFilter<>(CtTry.class));
            if (tryStatement != null) {
                candidates.add(tryStatement);
            }
        }

        for (CtTry tryStatement : candidates) {
            if (hasNonLegacyCheckedExceptions(tryStatement.getBody())) {
                continue;
            }

            boolean removedIOExceptionCatch = tryStatement.getCatchers().removeIf(catcher -> {
                CtTypeReference<?> type = catcher.getParameter() == null ? null : catcher.getParameter().getType();
                return type != null && IO_EXCEPTION.equals(type.getQualifiedName());
            });

            if (!removedIOExceptionCatch) {
                continue;
            }

            if (tryStatement.getCatchers().isEmpty() && tryStatement.getFinalizer() == null) {
                tryStatement.replace(tryStatement.getBody().clone());
            }
        }

        launcher.prettyprint();
    }

    private static boolean isLegacyJodaPrintTo(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !"printTo".equals(executable.getSimpleName())) {
            return false;
        }

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null || !DATE_TIME_FORMATTER.equals(declaringType.getQualifiedName())) {
            return false;
        }

        List<CtExpression<?>> arguments = invocation.getArguments();
        if (arguments.isEmpty()) {
            return false;
        }

        CtTypeReference<?> firstArgType = arguments.get(0).getType();
        return firstArgType != null && STRING_BUILDER.equals(firstArgType.getQualifiedName());
    }

    private static boolean hasNonLegacyCheckedExceptions(CtBlock<?> body) {
        if (body == null) {
            return false;
        }

        for (CtInvocation<?> invocation : body.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isLegacyJodaPrintTo(invocation)) {
                continue;
            }

            CtExecutable<?> executable = invocation.getExecutable() == null ? null : invocation.getExecutable().getExecutableDeclaration();
            if (executable == null) {
                continue;
            }

            for (CtTypeReference<?> thrownType : executable.getThrownTypes()) {
                if (thrownType == null) {
                    continue;
                }
                String qn = thrownType.getQualifiedName();
                if (!"java.lang.RuntimeException".equals(qn) && !"java.lang.Error".equals(qn)) {
                    return true;
                }
            }
        }

        return false;
    }
}
