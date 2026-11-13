package github.chains;

import java.util.List;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String DATE_TIME_FORMATTER = "org.joda.time.format.DateTimeFormatter";
    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String STRING_BUILDER = "java.lang.StringBuilder";
    private static final String STRING_BUFFER = "java.lang.StringBuffer";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        final String inputDir = args[0];
        final String outputDir = args.length == 2 ? args[1] : inputDir + "-spoon-out";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new AbstractProcessor<CtTry>() {
            @Override
            public void process(CtTry tryBlock) {
                if (!isUnnecessaryIOExceptionTry(tryBlock)) {
                    return;
                }

                CtElement body = tryBlock.getBody();
                if (body != null) {
                    tryBlock.replace(body.clone());
                }
            }
        });
        launcher.run();
    }

    private static boolean isUnnecessaryIOExceptionTry(CtTry tryBlock) {
        if (tryBlock == null || tryBlock.getCatchers().size() != 1 || tryBlock.getBody() == null) {
            return false;
        }

        if (!isIOExceptionCatch(tryBlock.getCatchers().get(0))) {
            return false;
        }

        List<CtInvocation<?>> invocations = tryBlock.getBody().getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        if (invocations.isEmpty()) {
            return false;
        }

        for (CtInvocation<?> invocation : invocations) {
            if (!isSafeInvocation(invocation)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isIOExceptionCatch(spoon.reflect.code.CtCatch ctCatch) {
        if (ctCatch == null || ctCatch.getParameter() == null || ctCatch.getParameter().getType() == null) {
            return false;
        }

        CtTypeReference<?> type = ctCatch.getParameter().getType();
        return IO_EXCEPTION.equals(type.getQualifiedName());
    }

    private static boolean isSafeInvocation(CtInvocation<?> invocation) {
        if (invocation == null) {
            return false;
        }

        CtExecutableReference<?> executable = invocation.getExecutable();
        CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
        String declaringTypeName = declaringType == null ? null : declaringType.getQualifiedName();

        if (STRING_BUILDER.equals(declaringTypeName) || STRING_BUFFER.equals(declaringTypeName)) {
            return true;
        }

        if (!DATE_TIME_FORMATTER.equals(declaringTypeName)) {
            return false;
        }

        if (executable == null || !"printTo".equals(executable.getSimpleName()) || invocation.getArguments().isEmpty()) {
            return false;
        }

        CtTypeReference<?> firstArgType = invocation.getArguments().get(0).getType();
        if (firstArgType == null) {
            return false;
        }

        String firstArgTypeName = firstArgType.getQualifiedName();
        return STRING_BUILDER.equals(firstArgTypeName) || STRING_BUFFER.equals(firstArgTypeName);
    }
}
