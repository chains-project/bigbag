package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.EnumSet;
import java.util.List;

public class Main {

    private static final String OLD_BUILDER = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_BUILDER = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String RESOLVER = "org.apache.maven.project.ProjectDependenciesResolver";
    private static final String COMPONENT = "org.apache.maven.plugins.annotations.Component";
    private static final String FIELD_NAME = "projectDependenciesResolver";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : args[0];

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);

        launcher.buildModel();

        var factory = launcher.getFactory();
        CtTypeReference<?> oldBuilderType = factory.Type().createReference(OLD_BUILDER);
        CtTypeReference<?> newBuilderType = factory.Type().createReference(NEW_BUILDER);
        CtTypeReference<?> resolverType = factory.Type().createReference(RESOLVER);
        CtTypeReference<?> componentType = factory.Type().createReference(COMPONENT);

        List<CtConstructorCall> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            if (!call.getType().equals(oldBuilderType)) {
                continue;
            }

            CtType<?> owner = call.getParent(CtType.class);
            if (!(owner instanceof CtClass<?> ctClass)) {
                continue;
            }

            String resolverFieldName = ensureResolverField(ctClass, resolverType, componentType);
            String replacement = "new " + NEW_BUILDER + "(" + resolverFieldName + ")";
            call.replace(factory.Code().createCodeSnippetExpression(replacement));
        }

        launcher.prettyprint();
    }

    private static String ensureResolverField(CtClass<?> ctClass, CtTypeReference<?> resolverType, CtTypeReference<?> componentType) {
        for (CtField<?> field : ctClass.getFields()) {
            if (field.getType() != null
                && resolverType.getQualifiedName().equals(field.getType().getQualifiedName())) {
                return field.getSimpleName();
            }
        }

        CtField<Object> field = ctClass.getFactory().Core().createField();
        field.setSimpleName(FIELD_NAME);
        field.setType(resolverType);
        field.addModifier(ModifierKind.PROTECTED);
        @SuppressWarnings({"rawtypes", "unchecked"})
        CtAnnotation<?> annotation = ctClass.getFactory().createAnnotation((CtTypeReference) componentType);
        field.addAnnotation(annotation);
        ctClass.addField(field);
        return FIELD_NAME;
    }
}
