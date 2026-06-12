package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_BUILDER = "org.hibernate.service.ServiceRegistryBuilder";
    private static final String NEW_BUILDER = "org.hibernate.boot.registry.StandardServiceRegistryBuilder";
    private static final String OLD_BUILD_METHOD = "buildServiceRegistry";
    private static final String NEW_BUILD_METHOD = "build";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        if (args.length > 1) {
            launcher.setSourceOutputDirectory(args[1]);
        } else {
            launcher.setSourceOutputDirectory(args[0]);
        }

        launcher.buildModel();

        CtTypeReference<?> newBuilderType = launcher.getFactory().Type().createReference(NEW_BUILDER);

        List<CtConstructorCall<?>> constructorCalls = launcher.getModel().getElements(
                new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            CtTypeReference<?> type = constructorCall.getType();
            if (type != null && OLD_BUILDER.equals(type.getQualifiedName())) {
                constructorCall.setType(newBuilderType.clone());
            }
        }

        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!OLD_BUILD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtExecutableReference<?> executable = invocation.getExecutable();
            executable.setSimpleName(NEW_BUILD_METHOD);
            if (executable.getDeclaringType() != null && OLD_BUILDER.equals(executable.getDeclaringType().getQualifiedName())) {
                executable.setDeclaringType(newBuilderType.clone());
            }
        }

        List<CtCompilationUnit> compilationUnits = launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class));
        for (CtCompilationUnit compilationUnit : compilationUnits) {
            List<spoon.reflect.declaration.CtImport> filteredImports = new ArrayList<>();
            for (spoon.reflect.declaration.CtImport ctImport : compilationUnit.getImports()) {
                if (ctImport.getReference() == null) {
                    filteredImports.add(ctImport);
                    continue;
                }
                if (!OLD_BUILDER.equals(ctImport.getReference().getSimpleName())
                        && !OLD_BUILDER.equals(ctImport.getReference().toString())) {
                    filteredImports.add(ctImport);
                }
            }
            compilationUnit.setImports(filteredImports);
        }

        launcher.prettyprint();
        cleanupDeprecatedImport(Paths.get(launcher.getEnvironment().getSourceOutputDirectory().getPath()));
    }

    private static void cleanupDeprecatedImport(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    content = content.replace("import org.hibernate.service.ServiceRegistryBuilder;\n", "");
                    content = content.replace("import org.hibernate.service.ServiceRegistryBuilder;\r\n", "");
                    Files.writeString(path, content, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
