package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_STRING_UTILS = "liquibase.util.StringUtils";
    private static final String NEW_STRING_UTILS = "liquibase.util.StringUtil";
    private static final String EXECUTOR_SERVICE = "liquibase.executor.ExecutorService";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> <outputSourceDir>");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = Paths.get(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(outputDir);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        CtModel model = launcher.getModel();
        rewriteStringUtilsReferences(model, launcher);
        rewriteStringUtilsImports(model, launcher);
        rewriteExecutorServiceSingletonCalls(model, launcher);

        launcher.prettyprint();
        cleanupGeneratedSources(outputDir);
    }

    private static void rewriteStringUtilsReferences(CtModel model, Launcher launcher) {
        List<CtTypeReference> references = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference reference : references) {
            if (OLD_STRING_UTILS.equals(reference.getQualifiedName()) || "StringUtils".equals(reference.getSimpleName())) {
                reference.replace(launcher.getFactory().createReference(NEW_STRING_UTILS));
            }
        }
    }

    private static void rewriteStringUtilsImports(CtModel model, Launcher launcher) {
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.toString().contains(OLD_STRING_UTILS)) {
                ctImport.delete();
            }
        }
    }

    private static void rewriteExecutorServiceSingletonCalls(CtModel model, Launcher launcher) {
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : invocations) {
            if (!"getExecutor".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            if (!(invocation.getTarget() instanceof CtInvocation)) {
                continue;
            }

            CtInvocation targetInvocation = (CtInvocation) invocation.getTarget();
            if (!"getInstance".equals(targetInvocation.getExecutable().getSimpleName())) {
                continue;
            }

            if (targetInvocation.getTarget() == null || !EXECUTOR_SERVICE.equals(targetInvocation.getExecutable().getDeclaringType().getQualifiedName())) {
                continue;
            }

            targetInvocation.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                    "liquibase.Scope.getCurrentScope().getSingleton(liquibase.executor.ExecutorService.class)"));
        }
    }

    private static void cleanupGeneratedSources(Path outputDir) throws Exception {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    content = content.replace("import liquibase.util.StringUtils;\n", "");
                    content = content.replace("import liquibase.util.StringUtils;\r\n", "");
                    Files.writeString(path, content);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
