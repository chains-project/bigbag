package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;


public class Main {
    private static final String OLD_OWNER = "liquibase.executor.ExecutorService";
    private static final String OLD_METHOD = "getInstance";
    private static final String NEW_SCOPE = "liquibase.Scope";
    private static final String NEW_METHOD = "getCurrentScope";
    private static final String NEW_SERVICE = "getSingleton";
    private static final String OLD_STRING_UTILS = "liquibase.util.StringUtils";
    private static final String NEW_STRING_UTIL = "liquibase.util.StringUtil";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> executorServiceType = factory.Type().createReference(OLD_OWNER);
        CtTypeReference<?> scopeType = factory.Type().createReference(NEW_SCOPE);
        CtTypeReference<?> oldStringUtilsType = factory.Type().createReference(OLD_STRING_UTILS);
        CtTypeReference<?> newStringUtilType = factory.Type().createReference(NEW_STRING_UTIL);

        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (oldStringUtilsType.getQualifiedName().equals(typeReference.getQualifiedName())) {
                typeReference.replace(newStringUtilType.clone());
            }
        }

        for (CtCompilationUnit compilationUnit : launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            compilationUnit.getImports().removeIf(ctImport -> ctImport.getReference() instanceof CtTypeReference<?> importType
                    && oldStringUtilsType.getQualifiedName().equals(importType.getQualifiedName()));
        }

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!matchesOldSingletonCall(invocation, executorServiceType, OLD_METHOD)) {
                continue;
            }

            CtInvocation<?> scopeInvocation = factory.Code().createInvocation(
                    factory.Code().createTypeAccess(scopeType),
                    createExecutable(factory, scopeType, NEW_METHOD)
            );

            CtInvocation<?> singletonInvocation = factory.Code().createInvocation(
                    scopeInvocation,
                    createExecutable(factory, scopeType, NEW_SERVICE),
                    factory.Code().createClassAccess(executorServiceType)
            );

            invocation.replace(singletonInvocation);
        }

        launcher.prettyprint();
    }

    private static boolean matchesOldSingletonCall(CtInvocation<?> invocation, CtTypeReference<?> ownerType, String methodName) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !methodName.equals(executable.getSimpleName())) {
            return false;
        }

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null || !ownerType.getQualifiedName().equals(declaringType.getQualifiedName())) {
            return false;
        }

        return invocation.getArguments().isEmpty();
    }

    private static CtExecutableReference<?> createExecutable(Factory factory, CtTypeReference<?> typeReference, String methodName) {
        return factory.Core().createExecutableReference()
                .setDeclaringType(typeReference)
                .setSimpleName(methodName);
    }
}
