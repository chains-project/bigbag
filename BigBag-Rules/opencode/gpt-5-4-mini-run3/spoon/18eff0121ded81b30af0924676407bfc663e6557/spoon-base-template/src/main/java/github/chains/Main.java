package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVASCRIPT_RESULT = "getJavaScriptResult";
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+com\\.gargoylesoftware\\.htmlunit\\.ScriptResult;\\s*$", Pattern.MULTILINE);
    private static final Pattern CHAIN_PATTERN = Pattern.compile("new\\s+ScriptResult\\(([^;]+?)\\)\\.getJavaScriptResult\\(\\)");
    private static final Pattern DECL_PATTERN = Pattern.compile("ScriptResult\\s+(\\w+)\\s*=\\s*new\\s+ScriptResult\\(([^;]+?)\\);", Pattern.MULTILINE);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input;
        Files.createDirectories(output);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.addProcessor(new ScriptResultConstructorProcessor());
        launcher.addProcessor(new ScriptResultGetterProcessor());
        launcher.addProcessor(new OldScriptResultTypeProcessor());
        launcher.addProcessor(new OldScriptResultImportProcessor());
        launcher.run();

        rewriteGeneratedSources(output);
    }

    private static void rewriteGeneratedSources(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }

        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String updated = IMPORT_PATTERN.matcher(content).replaceAll("");
                        updated = CHAIN_PATTERN.matcher(updated).replaceAll("$1");
                        updated = DECL_PATTERN.matcher(updated).replaceAll("Object $1 = $2;");
                        if (!updated.equals(content)) {
                            Files.writeString(path, updated);
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static boolean isOldScriptResult(CtTypeReference<?> type) {
        return type != null && OLD_SCRIPT_RESULT.equals(type.getQualifiedName());
    }

    private static class ScriptResultConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> call) {
            if (!isOldScriptResult(call.getType()) || call.getArguments().isEmpty()) {
                return;
            }

            call.replace(call.getArguments().get(0).clone());
        }
    }

    private static class ScriptResultGetterProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!GET_JAVASCRIPT_RESULT.equals(invocation.getExecutable().getSimpleName())
                    || !invocation.getArguments().isEmpty()) {
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }

            if (target instanceof CtConstructorCall && isOldScriptResult(((CtConstructorCall<?>) target).getType())) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                if (!constructorCall.getArguments().isEmpty()) {
                    invocation.replace(constructorCall.getArguments().get(0).clone());
                }
                return;
            }

            invocation.replace(target.clone());
        }
    }

    private static class OldScriptResultTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeReference) {
            if (!isOldScriptResult(typeReference)) {
                return;
            }

            typeReference.replace(getFactory().Type().objectType());
        }
    }

    private static class OldScriptResultImportProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport importRef) {
            if (importRef.getReference() != null && OLD_SCRIPT_RESULT.equals(importRef.getReference().toString())) {
                importRef.delete();
            }
        }
    }
}
