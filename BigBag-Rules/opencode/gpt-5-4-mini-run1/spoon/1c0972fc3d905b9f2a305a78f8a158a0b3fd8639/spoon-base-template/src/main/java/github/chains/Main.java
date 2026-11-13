package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String REQUIRED_ARG_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        CtTypeReference<?> oldTypeRef = launcher.getFactory().Type().createReference(OLD_TYPE);
        CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(NEW_TYPE);
        CtTypeReference<?> requiredArgTypeRef = launcher.getFactory().Type().createReference(REQUIRED_ARG_TYPE);

        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(newTypeRef.clone());
            }
        }

        for (CtConstructorCall<?> constructorCall : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (constructorCall.getType() != null && OLD_TYPE.equals(constructorCall.getType().getQualifiedName())) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                CtConstructorCall replacement = launcher.getFactory().Code().createConstructorCall((CtTypeReference) newTypeRef.clone());
                replacement.addArgument(launcher.getFactory().Code().createCodeSnippetExpression("null"));
                constructorCall.replace(replacement);
            }
        }

        launcher.prettyprint();
        cleanupLegacyImports(Paths.get(outputDir));
    }

    private static CtExpression<?> resolveCompatibleExpression(CtConstructorCall<?> anchor, CtTypeReference<?> requiredType) {
        CtExecutable<?> executable = anchor.getParent(CtExecutable.class);
        if (executable != null) {
            for (CtParameter<?> parameter : executable.getParameters()) {
                if (parameter.getType() != null && requiredType.getQualifiedName().equals(parameter.getType().getQualifiedName())) {
                    return anchor.getFactory().Code().createVariableRead(parameter.getReference(), false);
                }
            }
        }

        CtType<?> enclosingType = anchor.getParent(CtType.class);
        if (enclosingType != null) {
            for (CtField<?> field : enclosingType.getFields()) {
                if (field.getType() != null && requiredType.getQualifiedName().equals(field.getType().getQualifiedName())) {
                    return anchor.getFactory().Code().createVariableRead(field.getReference(), field.isStatic());
                }
            }
        }

        return anchor.getFactory().Code().createCodeSnippetExpression("null");
    }

    private static void cleanupLegacyImports(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8)
                            .replace("import org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder;\n", "")
                            .replace("new DefaultDependencyGraphBuilder()", "new DefaultDependencyGraphBuilder(null)");
                    Files.writeString(path, content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
