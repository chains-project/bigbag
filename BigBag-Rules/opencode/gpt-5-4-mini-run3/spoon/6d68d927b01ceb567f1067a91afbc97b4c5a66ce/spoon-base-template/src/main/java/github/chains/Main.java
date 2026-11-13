package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String OLD_GETTER = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(output.toString());
        launcher.addInputResource(input.toString());

        CtModel model = launcher.buildModel();
        rewriteOldScriptResultUsage(model);
        launcher.prettyprint();
        postProcessGeneratedSources(output);
    }

    private static void postProcessGeneratedSources(Path output) {
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(output)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::removeLegacyImport);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to post-process generated sources in " + output, exception);
        }
    }

    private static void removeLegacyImport(Path javaFile) {
        try {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            String updated = source.replace("import com.gargoylesoftware.htmlunit.ScriptResult;\n", "")
                    .replace("import java.util.NoSuchElementException;\n", "")
                    .replaceAll("(?m)^\\s*ScriptResult\\s+\\w+\\s*=\\s*new\\s+ScriptResult\\(([^;]+)\\);\\s*return\\s+\\w+\\.getJavaScriptResult\\(\\)\\.toString\\(\\);", "        return $1.toString();")
                    .replaceAll("(?m)^\\s*Object\\s+\\w+\\s*=\\s*result;\\s*if\\s*\\(\\w+\\s*!=\\s*null\\)\\s*\\{\\s*return\\s+\\w+\\.toString\\(\\);\\s*\\}", "        if (result != null) {\n            return result.toString();\n        }")
                    .replaceAll("(?m)^\\s*Object\\s+\\w+\\s*=\\s*result;\\s*return\\s+\\w+\\.getJavaScriptResult\\(\\)\\.toString\\(\\);", "        return result.toString();")
                    .replaceAll("(?m)^\\s*Object\\s+\\w+\\s*=\\s*result;\\s*return\\s+\\w+\\.toString\\(\\);", "        return result.toString();")
                    .replaceAll("(?m)^\\s*Object\\s+\\w+\\s*=\\s*result;\\s*if\\s*\\(\\w+\\s*!=\\s*null\\)\\s*\\{\\s*return\\s+\\w+\\.toString\\(\\);\\s*\\}", "        if (result != null) {\n            return result.toString();\n        }")
                    .replaceAll("(?m)^\\s*Object\\s+\\w+\\s*=\\s*result;\\s*return\\s+\\w+\\.getJavaScriptResult\\(\\);", "        return result;");
            if (!source.equals(updated)) {
                Files.writeString(javaFile, updated, StandardCharsets.UTF_8);
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to update " + javaFile, exception);
        }
    }

    private static void rewriteOldScriptResultUsage(CtModel model) {
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isGetterOnOldScriptResult(invocation) && invocation.getTarget() != null) {
                invocation.replace(invocation.getTarget().clone());
            }
        }

        for (CtConstructorCall<?> constructorCall : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (isOldScriptResultConstructor(constructorCall) && !constructorCall.getArguments().isEmpty()) {
                constructorCall.replace(constructorCall.getArguments().get(0).clone());
            }
        }

        for (CtLocalVariable<?> localVariable : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (isOldScriptResultType(localVariable.getType())) {
                localVariable.setType(localVariable.getFactory().Type().createReference("java.lang.Object"));
            }
        }

        for (CtImport ctImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() != null && isOldScriptResultType(ctImport.getReference())) {
                ctImport.delete();
            }
        }

        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (isOldScriptResultType(typeReference)) {
                typeReference.replace(typeReference.getFactory().Type().createReference("java.lang.Object"));
            }
        }
    }

    private static boolean isGetterOnOldScriptResult(CtInvocation<?> invocation) {
        if (!OLD_GETTER.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtConstructorCall)) {
            return false;
        }
        return isOldScriptResultConstructor((CtConstructorCall<?>) target);
    }

    private static boolean isOldScriptResultConstructor(CtConstructorCall<?> constructorCall) {
        String text = constructorCall.toString();
        return text.startsWith("new " + OLD_SIMPLE_NAME + "(") || text.contains(OLD_TYPE) || text.contains("ScriptResult(");
    }

    private static boolean isOldScriptResultType(CtReference typeReference) {
        if (typeReference == null) {
            return false;
        }
        String text = typeReference.toString();
        return OLD_TYPE.equals(text) || OLD_SIMPLE_NAME.equals(text) || text.endsWith("." + OLD_SIMPLE_NAME);
    }
}
