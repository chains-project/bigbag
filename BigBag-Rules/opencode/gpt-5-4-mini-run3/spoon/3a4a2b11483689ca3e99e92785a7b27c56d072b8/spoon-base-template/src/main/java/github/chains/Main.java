package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String REMOVED_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String WRAPPED_METHOD = "getJavaScriptResult";
    private static final String CONFLICTING_IMPORT = "java.util.NoSuchElementException";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
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

        removeBrokenImports(launcher.getModel().getAllTypes());

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!WRAPPED_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            if (invocation.getTarget() == null) {
                continue;
            }

            CtTypeReference<?> targetType = invocation.getTarget().getType();
            if (targetType == null || !REMOVED_TYPE.equals(targetType.getQualifiedName())) {
                continue;
            }

            if (invocation.getTarget().toString() == null) {
                continue;
            }

            invocation.replace(invocation.getTarget().clone());
        }

        launcher.prettyprint();
    }

    private static void removeBrokenImports(Iterable<CtType<?>> types) {
        for (CtType<?> type : types) {
            var compilationUnit = type.getPosition().getCompilationUnit();
            if (compilationUnit == null) {
                continue;
            }

            var filteredImports = new java.util.ArrayList<CtImport>();
            for (CtImport anImport : compilationUnit.getImports()) {
                if (anImport.getReference() instanceof CtTypeReference<?> reference
                        && (REMOVED_TYPE.equals(reference.getQualifiedName())
                        || CONFLICTING_IMPORT.equals(reference.getQualifiedName()))) {
                    continue;
                }
                filteredImports.add(anImport);
            }

            compilationUnit.setImports(filteredImports);
        }
    }
}
