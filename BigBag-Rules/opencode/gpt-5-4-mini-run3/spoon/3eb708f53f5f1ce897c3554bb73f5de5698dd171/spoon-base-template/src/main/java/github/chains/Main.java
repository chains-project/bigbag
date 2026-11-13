package github.chains;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Map<String, String> TYPE_MAPPINGS = new LinkedHashMap<>();
    private static final Map<String, String> METHOD_MAPPINGS = new LinkedHashMap<>();

    static {
        TYPE_MAPPINGS.put(
                "com.google.api.services.translate.Translate",
                "com.google.api.services.translate.v3.Translate");
        TYPE_MAPPINGS.put(
                "com.google.api.services.translate.model.LanguagesResource",
                "com.google.api.services.translate.v3.model.SupportedLanguage");
        TYPE_MAPPINGS.put(
                "com.google.api.services.translate.model.DetectionsResourceItems",
                "com.google.api.services.translate.v3.model.DetectedLanguage");
        TYPE_MAPPINGS.put(
                "com.google.api.services.translate.model.TranslationsResource",
                "com.google.api.services.translate.v3.model.Translation");

        METHOD_MAPPINGS.put("getLanguage", "getLanguageCode");
        METHOD_MAPPINGS.put("setLanguage", "setLanguageCode");
        METHOD_MAPPINGS.put("getName", "getDisplayName");
        METHOD_MAPPINGS.put("setName", "setDisplayName");
        METHOD_MAPPINGS.put("getDetectedSourceLanguage", "getDetectedLanguageCode");
        METHOD_MAPPINGS.put("setDetectedSourceLanguage", "setDetectedLanguageCode");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: Main <inputSourceDir> <outputDir>");
        }

        Path input = Paths.get(args[0]);
        File output = Paths.get(args[1]).toFile();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();

        rewriteTypes(launcher);
        rewriteExecutables(launcher);
        rewriteTranslateClientCalls(launcher);

        launcher.prettyprint();
    }

    private static void rewriteTypes(Launcher launcher) {
        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String replacement = TYPE_MAPPINGS.get(typeReference.getQualifiedName());
            if (replacement != null) {
                typeReference.setSimpleName(replacement.substring(replacement.lastIndexOf('.') + 1));
                typeReference.setPackage(launcher.getFactory().Package().createReference(replacement.substring(0, replacement.lastIndexOf('.'))));
            }
        }
    }

    private static void rewriteExecutables(Launcher launcher) {
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            String replacement = METHOD_MAPPINGS.get(executable.getSimpleName());
            if (replacement != null) {
                executable.setSimpleName(replacement);
            }
        }
    }

    private static void rewriteTranslateClientCalls(Launcher launcher) {
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            String name = executable.getSimpleName();
            if (!"list".equals(name) && !"languages".equals(name) && !"detections".equals(name)
                    && !"translations".equals(name)) {
                continue;
            }
            CtElement parent = invocation.getParent();
            if (parent instanceof CtInvocation<?>) {
                CtInvocation<?> parentInvocation = (CtInvocation<?>) parent;
                String parentName = parentInvocation.getExecutable().getSimpleName();
                if ("detections".equals(name) || "languages".equals(name) || "translations".equals(name)) {
                    parentInvocation.getExecutable().setSimpleName("projects");
                }
            }
        }
    }
}
