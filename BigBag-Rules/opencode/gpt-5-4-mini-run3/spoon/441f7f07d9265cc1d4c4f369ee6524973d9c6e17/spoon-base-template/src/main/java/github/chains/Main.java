package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JS_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputDir> [outputDir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-spooned");

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.buildModel();

        transform(launcher);
        launcher.prettyprint();
        cleanupLegacyImports(outputDir);
    }

    private static void transform(Launcher launcher) {
        rewriteLocalVariables(launcher);
        rewriteDirectConstructorCalls(launcher);
        rewriteGetterInvocations(launcher);
        removeOldImports(launcher);
    }

    private static void rewriteLocalVariables(Launcher launcher) {
        List<CtLocalVariable> locals = launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            if (!isOldScriptResult(local.getType()) || !(local.getAssignment() instanceof CtConstructorCall)) {
                continue;
            }

            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) local.getAssignment();
            if (!isOldScriptResult(constructorCall.getType()) || constructorCall.getArguments().size() != 1) {
                continue;
            }

            CtExpression<?> payload = constructorCall.getArguments().get(0);
            local.setType(payload.getType());
            @SuppressWarnings({"rawtypes", "unchecked"})
            CtExpression assignment = payload;
            local.setAssignment(assignment);
        }
    }

    private static void rewriteDirectConstructorCalls(Launcher launcher) {
        List<CtConstructorCall> constructorCalls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            if (isOldScriptResult(constructorCall.getType()) && constructorCall.getArguments().size() == 1) {
                constructorCall.replace(constructorCall.getArguments().get(0));
            }
        }
    }

    private static void rewriteGetterInvocations(Launcher launcher) {
        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (invocation.getTarget() != null && GET_JS_RESULT.equals(invocation.getExecutable().getSimpleName())) {
                invocation.replace(invocation.getTarget());
            }
        }
    }

    private static void removeOldImports(Launcher launcher) {
        List<CtImport> imports = launcher.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference
                    && OLD_SCRIPT_RESULT.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName())) {
                ctImport.delete();
            }
        }
    }

    private static void cleanupLegacyImports(Path outputDir) {
        try {
            Files.walk(outputDir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::cleanupFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clean generated sources", e);
        }
    }

    private static void cleanupFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String cleaned = content.replace("import com.gargoylesoftware.htmlunit.ScriptResult;\n", "");
            if (!cleaned.equals(content)) {
                Files.writeString(file, cleaned, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clean generated source " + file, e);
        }
    }

    private static boolean isOldScriptResult(spoon.reflect.reference.CtTypeReference<?> reference) {
        return reference != null && OLD_SCRIPT_RESULT.equals(reference.getQualifiedName());
    }
}
