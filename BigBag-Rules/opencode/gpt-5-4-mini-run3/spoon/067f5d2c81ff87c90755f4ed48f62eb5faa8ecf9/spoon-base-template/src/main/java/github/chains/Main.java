package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = Map.of(
            "org.apache.thrift.transport.TFramedTransport",
            "org.apache.thrift.transport.layered.TFramedTransport",
            "org.apache.thrift.transport.TFastFramedTransport",
            "org.apache.thrift.transport.layered.TFastFramedTransport"
    );

    private static final Map<String, String> ZERO_ARG_FACTORY_METHODS = Map.of(
            "org.apache.thrift.TSerializer", "createThriftSerializer",
            "org.apache.thrift.TDeserializer", "createThriftDeserializer"
    );

    private static final Map<String, String> SIMPLE_TO_FQCN = Map.of(
            "TSerializer", "org.apache.thrift.TSerializer",
            "TDeserializer", "org.apache.thrift.TDeserializer"
    );

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2
                ? Paths.get(args[1])
                : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

        try {
            Files.walk(inputDir)
                    .filter(Files::isDirectory)
                    .forEach(dir -> {
                        Path relative = inputDir.relativize(dir);
                        Path target = relative.getNameCount() == 0 ? outputDir : outputDir.resolve(relative);
                        try {
                            Files.createDirectories(target);
                        } catch (Exception e) {
                            throw new RuntimeException("Unable to create output directory: " + target, e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Unable to prepare output directories under: " + outputDir, e);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        List<CtTypeReference<?>> references = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            String replacement = TYPE_RENAMES.get(reference.getQualifiedName());
            if (replacement != null) {
                reference.replace(launcher.getFactory().Type().createReference(replacement));
            }
        }

        List<CtConstructorCall<?>> constructorCalls = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        Set<String> preparedHelpers = new LinkedHashSet<>();
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            if (!constructorCall.getArguments().isEmpty()) {
                continue;
            }
            CtTypeReference<?> constructedType = constructorCall.getExecutable().getDeclaringType();
            String constructedTypeName = constructedType == null ? null : constructedType.getQualifiedName();
            if (constructedTypeName == null) {
                String snippet = constructorCall.toString();
                if (snippet.startsWith("new ") && snippet.contains("(")) {
                    String simpleName = snippet.substring(4, snippet.indexOf('(')).trim();
                    constructedTypeName = SIMPLE_TO_FQCN.get(simpleName);
                }
            }
            String helperName = constructedTypeName == null ? null : ZERO_ARG_FACTORY_METHODS.get(constructedTypeName);
            if (helperName == null || !constructorCall.getArguments().isEmpty()) {
                continue;
            }

            CtType<?> owner = constructorCall.getParent(CtType.class);
            if (owner == null) {
                continue;
            }

            String helperKey = owner.getQualifiedName() + "::" + helperName;
            if (preparedHelpers.add(helperKey)) {
                addCheckedConstructorHelper(launcher, owner, helperName, constructedType.getQualifiedName());
            }

            CtExpression<?> replacement = launcher.getFactory().Code().createCodeSnippetExpression(helperName + "()");
            constructorCall.replace(replacement);
        }

        launcher.prettyprint();
        System.out.println("Wrote transformed sources to " + outputDir);
    }

    private static void addCheckedConstructorHelper(Launcher launcher, CtType<?> owner, String helperName, String targetType) {
        boolean alreadyPresent = owner.getMethodsByName(helperName).stream()
                .anyMatch(method -> method.getParameters().isEmpty());
        if (alreadyPresent) {
            return;
        }

        String body = "try { return new " + targetType + "(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }";
        CtMethod<?> helper = launcher.getFactory().Core().createMethod();
        helper.setSimpleName(helperName);
        helper.setType(launcher.getFactory().Type().createReference(targetType));
        helper.addModifier(ModifierKind.PRIVATE);
        helper.addModifier(ModifierKind.STATIC);
        helper.setBody(launcher.getFactory().Code().createCtBlock(
                launcher.getFactory().Code().createCodeSnippetStatement(body)));
        owner.addMethod(helper);
    }
}
