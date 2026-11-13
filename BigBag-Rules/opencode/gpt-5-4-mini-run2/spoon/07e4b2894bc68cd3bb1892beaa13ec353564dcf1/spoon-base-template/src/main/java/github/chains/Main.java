package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputSourceDir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args.length > 1 ? args[1] : args[0]);

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir.getAbsolutePath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> oldType = factory.Type().createReference(OLD_TYPE);

        for (CtCompilationUnit compilationUnit : launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            compilationUnit.getImports().removeIf(imported -> isOldImport(imported, oldType));
        }

        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (isOldImport(ctImport, oldType)) {
                ctImport.delete();
            }
        }

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if ("getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                if (invocation.getTarget() instanceof CtConstructorCall) {
                    CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) invocation.getTarget();
                    if (matchesOldType(constructorCall.getType(), oldType) && !constructorCall.getArguments().isEmpty()) {
                        invocation.replace(constructorCall.getArguments().get(0));
                    }
                }
            }
        }

        launcher.prettyprint();
    }

    private static boolean matchesOldType(CtTypeReference<?> candidate, CtTypeReference<?> oldType) {
        return candidate != null && oldType != null && OLD_TYPE.equals(candidate.getQualifiedName());
    }

    private static boolean isOldImport(CtImport imported, CtTypeReference<?> oldType) {
        return imported != null && imported.getReference() != null && OLD_TYPE.equals(imported.getReference().toString())
                || (imported != null && imported.getReference() != null && oldType != null && oldType.equals(imported.getReference()));
    }
}
