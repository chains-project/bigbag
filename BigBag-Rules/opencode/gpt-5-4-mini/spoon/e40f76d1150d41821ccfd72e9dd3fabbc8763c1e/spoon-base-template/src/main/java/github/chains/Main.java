package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());

        launcher.buildModel();
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (matchesOldPattern(invocation)) {
                CtExpression<?> replacement = replacementExpression(invocation);
                if (replacement != null) {
                    invocation.replace(replacement);
                }
            }
        }
        removeOldImports(launcher);
        launcher.prettyprint();
    }

    private static void removeOldImports(Launcher launcher) {
        for (CtCompilationUnit compilationUnit : launcher.getFactory().CompilationUnit().getMap().values()) {
            compilationUnit.setImports(compilationUnit.getImports().stream()
                    .filter(imported -> !isOldImport(imported))
                    .toList());
        }
    }

    private static boolean matchesOldPattern(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !OLD_METHOD.equals(executable.getSimpleName())) {
            return false;
        }
        CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtConstructorCall<?> constructorCall)) {
            return false;
        }
        CtTypeReference<?> type = constructorCall.getType();
        return constructorCall.getArguments().size() == 1
                && type != null
                && (OLD_TYPE.equals(type.getQualifiedName()) || "ScriptResult".equals(type.getSimpleName()));
    }

    private static CtExpression<?> replacementExpression(CtInvocation<?> invocation) {
        CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtConstructorCall<?> constructorCall && constructorCall.getArguments().size() == 1) {
            return constructorCall.getArguments().get(0).clone();
        }
        return target == null ? null : target.clone();
    }

    private static boolean matchesOldType(CtTypeReference<?> typeReference) {
        return typeReference != null
                && (OLD_TYPE.equals(typeReference.getQualifiedName()) || "ScriptResult".equals(typeReference.getSimpleName()));
    }

    private static boolean isOldImport(CtImport ctImport) {
        return ctImport.getReference() instanceof CtTypeReference<?> typeReference
                && OLD_TYPE.equals(typeReference.getQualifiedName());
    }
}
